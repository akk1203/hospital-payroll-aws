package com.hospital.payroll.service;

import com.hospital.payroll.model.AttendanceDay;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.DayStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import jakarta.enterprise.context.ApplicationScoped;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class AttendanceFileParser {

    private static final Pattern DATE_RANGE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*[~\\-]\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern TIME = Pattern.compile("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter INDIAN = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter INDIAN_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ParsedAttendance parse(byte[] data, String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        try (InputStream in = new ByteArrayInputStream(data)) {
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                return parseExcel(in);
            }
            return parseCsv(in);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read attendance file: " + ex.getMessage(), ex);
        }
    }

    private ParsedAttendance parseCsv(InputStream in) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parseLongCsv(reader);
        }
    }

    private ParsedAttendance parseLongCsv(BufferedReader reader) throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();
        Map<String, AttendancePerson> people = new LinkedHashMap<>();
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        try (CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                String name = firstPresent(record, "employee_name", "name");
                String code = firstPresent(record, "employee_code", "attendance_code", "id");
                String dateValue = firstPresent(record, "date");
                String punchesValue = firstPresent(record, "punches", "times");
                String timeIn = firstPresent(record, "time_in", "in_time", "intime");
                String timeOut = firstPresent(record, "time_out", "out_time", "outtime");
                String statusValue = firstPresent(record, "status");
                String notes = firstPresent(record, "notes");
                if (name == null || name.isBlank() || dateValue == null || dateValue.isBlank()) {
                    continue;
                }
                LocalDate date = parseDate(dateValue);
                periodStart = min(periodStart, date);
                periodEnd = max(periodEnd, date);
                AttendancePerson person = people.computeIfAbsent(personKey(code, name), key -> newPerson(code, name));
                AttendanceDay day = new AttendanceDay();
                day.setDate(date);
                day.setSourceEmployeeCode(code);
                day.setSourceEmployeeName(name);
                day.setNotes(notes);
                List<String> punches = WorkedHoursCalculator.combinePunches(timeIn, timeOut, punchesValue);
                if (punches.isEmpty()) {
                    punches = extractPunches(punchesValue);
                }
                day.setPunches(punches);
                day.setStatus(resolveStatus(statusValue, punches, notes));
                WorkedHoursCalculator.apply(day);
                person.getDays().add(day);
            }
        }
        return toResult(people, periodStart, periodEnd);
    }

    private ParsedAttendance parseExcel(InputStream in) throws Exception {
        byte[] data = in.readAllBytes();
        if (data.length == 0) {
            throw new IllegalArgumentException("The attendance file was empty.");
        }
        Workbook workbook;
        try {
            if (looksLikeXmlSpreadsheet(data)) {
                workbook = spreadsheetMlToWorkbook(data);
            } else {
                workbook = WorkbookFactory.create(new ByteArrayInputStream(data));
            }
        } catch (Exception ex) {
            try {
                workbook = spreadsheetMlToWorkbook(data);
            } catch (Exception ignored) {
                throw new IllegalArgumentException(
                        "This is not a readable Excel workbook. Download a fresh sample from this page after the latest API deploy, "
                                + "or in Excel use File → Save As → Excel Workbook (.xlsx). (" + ex.getMessage() + ")");
            }
        }
        try (Workbook opened = workbook) {
            return parseExcelWorkbook(opened);
        }
    }

    private ParsedAttendance parseExcelWorkbook(Workbook opened) {
        Sheet sheet = opened.getSheetAt(0);
        DataFormatter formatter = new DataFormatter();
        Row header = sheet.getRow(0);
        String headerText = header == null ? "" : rowText(header, formatter).toLowerCase(Locale.ROOT);
        if (headerText.contains("employee_name") || headerText.contains("time_in") || headerText.contains("in_time")) {
            return parseLongExcel(sheet, formatter);
        }
        return parseWideReport(sheet, formatter);
    }

    private boolean looksLikeXmlSpreadsheet(byte[] data) {
        if (data.length >= 2 && data[0] == 'P' && data[1] == 'K') {
            return false;
        }
        if (data.length >= 4 && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF) {
            return false;
        }
        String probe = probeText(data);
        return probe.contains("Excel.Sheet")
                || probe.contains("office:spreadsheet")
                || probe.contains("urn:schemas-microsoft-com:office:spreadsheet")
                || (probe.contains("<?xml") && probe.toLowerCase(Locale.ROOT).contains("workbook"));
    }

    private String probeText(byte[] data) {
        int max = Math.min(data.length, 8192);
        if (max >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) {
            return new String(data, 2, max - 2, StandardCharsets.UTF_16LE);
        }
        if (max >= 2 && data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {
            return new String(data, 2, max - 2, StandardCharsets.UTF_16BE);
        }
        if (max >= 2 && data[0] == '<' && data[1] == 0) {
            return new String(data, 0, max, StandardCharsets.UTF_16LE);
        }
        int start = 0;
        if (max >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        return new String(data, start, max - start, StandardCharsets.UTF_8);
    }

    private Workbook spreadsheetMlToWorkbook(byte[] data) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(data));
        String ns = "urn:schemas-microsoft-com:office:spreadsheet";
        NodeList xmlRows = doc.getElementsByTagNameNS(ns, "Row");
        if (xmlRows.getLength() == 0) {
            xmlRows = doc.getElementsByTagName("Row");
        }
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Attendance Record Report");
        for (int r = 0; r < xmlRows.getLength(); r++) {
            Element xmlRow = (Element) xmlRows.item(r);
            Row row = sheet.createRow(r);
            NodeList xmlCells = xmlRow.getElementsByTagNameNS(ns, "Cell");
            if (xmlCells.getLength() == 0) {
                xmlCells = xmlRow.getElementsByTagName("Cell");
            }
            int col = 0;
            for (int c = 0; c < xmlCells.getLength(); c++) {
                Element xmlCell = (Element) xmlCells.item(c);
                String index = xmlCell.getAttributeNS(ns, "Index");
                if (index == null || index.isBlank()) {
                    index = xmlCell.getAttribute("ss:Index");
                }
                if (index != null && !index.isBlank()) {
                    col = Integer.parseInt(index) - 1;
                }
                row.createCell(col).setCellValue(xmlCell.getTextContent().trim());
                col++;
            }
        }
        return workbook;
    }

    private ParsedAttendance parseLongExcel(Sheet sheet, DataFormatter formatter) {
        Row header = sheet.getRow(0);
        Map<String, Integer> cols = new LinkedHashMap<>();
        for (Cell cell : header) {
            cols.put(formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
        }
        Map<String, AttendancePerson> people = new LinkedHashMap<>();
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String nameFromHeader = cell(row, cols.get("employee_name"), formatter);
            String name = nameFromHeader.isBlank() ? cell(row, cols.get("name"), formatter) : nameFromHeader;
            String code = firstCell(row, formatter, cols, "employee_code", "attendance_code", "id");
            String dateValue = cell(row, cols.get("date"), formatter);
            if (name.isBlank() || dateValue.isBlank()) {
                continue;
            }
            LocalDate date = parseDate(dateValue);
            periodStart = min(periodStart, date);
            periodEnd = max(periodEnd, date);
            AttendancePerson person = people.computeIfAbsent(personKey(code, name), key -> newPerson(code, name));
            AttendanceDay day = new AttendanceDay();
            day.setDate(date);
            day.setSourceEmployeeName(name);
            day.setSourceEmployeeCode(code);
            String punchesValue = firstCell(row, formatter, cols, "punches", "times");
            String timeIn = firstCell(row, formatter, cols, "time_in", "in_time", "intime");
            String timeOut = firstCell(row, formatter, cols, "time_out", "out_time", "outtime");
            String statusValue = cell(row, cols.get("status"), formatter);
            String notes = cell(row, cols.get("notes"), formatter);
            List<String> punches = WorkedHoursCalculator.combinePunches(timeIn, timeOut, punchesValue);
            if (punches.isEmpty()) {
                punches = extractPunches(punchesValue);
            }
            day.setPunches(punches);
            day.setNotes(notes);
            day.setStatus(resolveStatus(statusValue, punches, notes));
            WorkedHoursCalculator.apply(day);
            person.getDays().add(day);
        }
        return toResult(people, periodStart, periodEnd);
    }

    private ParsedAttendance parseWideReport(Sheet sheet, DataFormatter formatter) {
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        for (int r = 0; r <= Math.min(8, sheet.getLastRowNum()); r++) {
            Matcher matcher = DATE_RANGE.matcher(rowText(sheet.getRow(r), formatter));
            if (matcher.find()) {
                periodStart = LocalDate.parse(matcher.group(1));
                periodEnd = LocalDate.parse(matcher.group(2));
                break;
            }
        }
        if (periodStart == null) {
            throw new IllegalArgumentException("Could not find Att. Time range like 2026-08-14 ~ 2026-09-02 in the Excel file.");
        }

        int headerRowIndex = -1;
        List<Integer> dateColumns = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<Integer> nums = new ArrayList<>();
            for (Cell cell : row) {
                String text = formatter.formatCellValue(cell).trim();
                if (text.matches("\\d{1,2}")) {
                    nums.add(cell.getColumnIndex());
                }
            }
            if (nums.size() >= 10) {
                headerRowIndex = r;
                dateColumns = nums;
                dates = expandDates(periodStart, periodEnd, row, dateColumns, formatter);
                break;
            }
        }
        if (headerRowIndex < 0) {
            throw new IllegalArgumentException("Could not find a header row of calendar day numbers.");
        }

        Map<String, AttendancePerson> people = new LinkedHashMap<>();
        int firstDateCol = dateColumns.get(0);
        for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String employeeId = findEmployeeId(row, firstDateCol, formatter);
            if (employeeId == null) {
                continue;
            }
            Row second = sheet.getRow(r + 1);
            if (second != null && findEmployeeId(second, firstDateCol, formatter) != null) {
                second = null;
            }
            String name = extractName(row, second, dateColumns, formatter);
            AttendancePerson person = people.computeIfAbsent(personKey(employeeId, name), key -> newPerson(employeeId, name));
            for (int i = 0; i < dateColumns.size(); i++) {
                int col = dateColumns.get(i);
                LocalDate date = dates.get(i);
                String top = cell(row, col, formatter);
                String bottom = second == null ? "" : cell(second, col, formatter);
                String combined = (top + " " + bottom).trim();
                AttendanceDay day = new AttendanceDay();
                day.setDate(date);
                day.setSourceEmployeeCode(employeeId);
                day.setSourceEmployeeName(name);
                day.setPunches(extractPunches(combined));
                day.setNotes(extractNotes(combined));
                day.setStatus(resolveStatus(combined, day.getPunches(), day.getNotes()));
                WorkedHoursCalculator.apply(day);
                person.getDays().add(day);
            }
            if (second != null) {
                r++;
            }
        }
        return toResult(people, periodStart, periodEnd);
    }

    private String findEmployeeId(Row row, int firstDateCol, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        for (int c = 0; c < firstDateCol; c++) {
            String text = cell(row, c, formatter);
            Matcher labelled = Pattern.compile("(?i)ID\\s*:\\s*(\\d+)").matcher(text);
            if (labelled.find()) {
                return labelled.group(1);
            }
            if (text.matches("\\d+")) {
                return text;
            }
        }
        return null;
    }

    private String extractName(Row first, Row second, List<Integer> dateColumns, DataFormatter formatter) {
        String fromLabel = findNameInRow(first, formatter);
        if (fromLabel.isBlank()) {
            fromLabel = findNameInRow(second, formatter);
        }
        if (!fromLabel.isBlank()) {
            return fromLabel;
        }
        int afterDates = dateColumns.get(dateColumns.size() - 1) + 1;
        for (Row row : List.of(first, second)) {
            if (row == null) {
                continue;
            }
            short last = row.getLastCellNum();
            for (int c = afterDates; c < last; c++) {
                String text = cell(row, c, formatter);
                if (isIgnorableNameCell(text)) {
                    continue;
                }
                return text.replaceFirst("(?i)^name\\s*:?\\s*", "").trim();
            }
        }
        return "";
    }

    private boolean isIgnorableNameCell(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String upper = text.trim().toUpperCase(Locale.ROOT);
        return upper.equals("NAME") || upper.equals("NAME:") || upper.startsWith("DEPT")
                || upper.equals("COMPANY") || TIME.matcher(text).find() || text.matches("\\d+");
    }

    private List<LocalDate> expandDates(LocalDate start, LocalDate end, Row header, List<Integer> cols, DataFormatter formatter) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = start;
        for (Integer col : cols) {
            int dayNumber = Integer.parseInt(formatter.formatCellValue(header.getCell(col)).trim());
            while (cursor.getDayOfMonth() != dayNumber && !cursor.isAfter(end)) {
                cursor = cursor.plusDays(1);
            }
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    private String findNameInRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        for (Cell cell : row) {
            String text = formatter.formatCellValue(cell).trim();
            if (text.toLowerCase(Locale.ROOT).startsWith("name")) {
                String value = text.replaceFirst("(?i)name\\s*:?\\s*", "").trim();
                if (!value.isBlank() && !isIgnorableNameCell(value)) {
                    return value;
                }
                Cell next = row.getCell(cell.getColumnIndex() + 1);
                String nextValue = formatter.formatCellValue(next).trim();
                return isIgnorableNameCell(nextValue) ? "" : nextValue;
            }
        }
        return "";
    }

    private DayStatus resolveStatus(String statusOrCell, List<String> punches, String notes) {
        String raw = (statusOrCell == null ? "" : statusOrCell) + " " + (notes == null ? "" : notes);
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.matches(".*\\bL\\b.*") || upper.contains("LEAVE")) {
            return DayStatus.LEAVE;
        }
        if (upper.contains("ABSENT")) {
            return DayStatus.ABSENT;
        }
        if (upper.contains("PRESENT") || (punches != null && !punches.isEmpty())) {
            return DayStatus.PRESENT;
        }
        return DayStatus.ABSENT;
    }

    private List<String> extractPunches(String text) {
        List<String> punches = new ArrayList<>();
        if (text == null) {
            return punches;
        }
        Matcher matcher = TIME.matcher(text);
        while (matcher.find()) {
            punches.add(matcher.group());
        }
        return punches;
    }

    private String extractNotes(String text) {
        if (text == null) {
            return null;
        }
        String withoutTimes = TIME.matcher(text).replaceAll("").replace(";", " ").trim();
        return withoutTimes.isBlank() ? null : withoutTimes;
    }

    private ParsedAttendance toResult(Map<String, AttendancePerson> people, LocalDate start, LocalDate end) {
        List<AttendancePerson> list = new ArrayList<>(people.values());
        list.sort(Comparator.comparing(AttendancePerson::getSourceEmployeeName, Comparator.nullsLast(String::compareToIgnoreCase)));
        if (start == null || end == null || list.isEmpty()) {
            throw new IllegalArgumentException("No attendance rows were found in the file.");
        }
        return new ParsedAttendance(start, end, list);
    }

    private AttendancePerson newPerson(String code, String name) {
        AttendancePerson person = new AttendancePerson();
        person.setSourceEmployeeCode(blankToNull(code));
        person.setSourceEmployeeName(name == null ? "" : name.trim());
        return person;
    }

    private String personKey(String code, String name) {
        return (code == null ? "" : code.trim()) + "|" + NameNormalizer.normalize(name);
    }

    private String firstPresent(CSVRecord record, String... headers) {
        for (String header : headers) {
            if (record.isMapped(header)) {
                String value = record.get(header);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private String firstCell(Row row, DataFormatter formatter, Map<String, Integer> cols, String... keys) {
        for (String key : keys) {
            Integer idx = cols.get(key);
            if (idx != null) {
                String value = cell(row, idx, formatter);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String cell(Row row, Integer index, DataFormatter formatter) {
        if (row == null || index == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private String rowText(Row row, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Cell cell : row) {
            builder.append(formatter.formatCellValue(cell)).append(' ');
        }
        return builder.toString();
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : List.of(ISO, INDIAN, INDIAN_SLASH)) {
            try {
                return LocalDate.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException("Unsupported date: " + value);
    }

    private LocalDate min(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return a.isBefore(b) ? a : b;
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        return a.isAfter(b) ? a : b;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ParsedAttendance(LocalDate periodStart, LocalDate periodEnd, List<AttendancePerson> people) {
    }
}
