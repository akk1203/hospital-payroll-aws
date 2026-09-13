package com.hospital.payroll.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Builds the printed Attendance Record Report grid: date-number columns,
 * two rows per employee (in on the first row, out on the second).
 */
public final class PrintedAttendanceReportFactory {

    public static final LocalDate PERIOD_START = LocalDate.of(2026, 8, 1);
    public static final LocalDate PERIOD_END = LocalDate.of(2026, 8, 31);
    public static final int[] DAY_NUMBERS = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
    };

    private PrintedAttendanceReportFactory() {
    }

    public static byte[] xlsxBytes() throws IOException {
        try (Workbook workbook = createWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static Workbook createWorkbook() {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Attendance Record Report");
        CellStyle title = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);

        CellStyle header = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle box = workbook.createCellStyle();
        box.setAlignment(HorizontalAlignment.CENTER);
        box.setVerticalAlignment(VerticalAlignment.CENTER);
        box.setBorderBottom(BorderStyle.THIN);
        box.setBorderTop(BorderStyle.THIN);
        box.setBorderLeft(BorderStyle.THIN);
        box.setBorderRight(BorderStyle.THIN);
        box.setWrapText(true);

        int lastCol = 1 + DAY_NUMBERS.length + 1; // ID + dates + Name + Dept
        Row titleRow = sheet.createRow(0);
        cell(titleRow, 0, "Attendance Record Report", title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        Row info = sheet.createRow(1);
        cell(info, 0, "Att. Time " + PERIOD_START + " ~ " + PERIOD_END, null);
        cell(info, lastCol - 1, "Tabulation 2026-08-31", null);

        Row headerRow = sheet.createRow(2);
        cell(headerRow, 0, "ID", header);
        for (int i = 0; i < DAY_NUMBERS.length; i++) {
            cell(headerRow, 1 + i, String.valueOf(DAY_NUMBERS[i]), header);
        }
        cell(headerRow, 1 + DAY_NUMBERS.length, "Name:", header);
        cell(headerRow, 2 + DAY_NUMBERS.length, "Dept.:", header);

        int rowIndex = 3;
        for (EmployeeBlock block : sampleEmployees()) {
            Row inRow = sheet.createRow(rowIndex++);
            Row outRow = sheet.createRow(rowIndex++);
            cell(inRow, 0, block.id(), box);
            cell(outRow, 0, "", box);
            for (int i = 0; i < DAY_NUMBERS.length; i++) {
                String[] punches = block.days().get(i);
                cell(inRow, 1 + i, punches[0], box);
                cell(outRow, 1 + i, punches[1], box);
            }
            cell(inRow, 1 + DAY_NUMBERS.length, "Name: " + block.name(), box);
            cell(outRow, 1 + DAY_NUMBERS.length, "", box);
            cell(inRow, 2 + DAY_NUMBERS.length, "Company", box);
            cell(outRow, 2 + DAY_NUMBERS.length, "", box);
        }

        sheet.setColumnWidth(0, 12 * 256);
        for (int i = 1; i <= DAY_NUMBERS.length; i++) {
            sheet.setColumnWidth(i, 9 * 256);
        }
        sheet.setColumnWidth(1 + DAY_NUMBERS.length, 22 * 256);
        sheet.setColumnWidth(2 + DAY_NUMBERS.length, 14 * 256);
        return workbook;
    }

    static List<EmployeeBlock> sampleEmployees() {
        return List.of(
                new EmployeeBlock("6", "DrDHARMIK", List.of(
                        pair("21:17", ""),
                        pair("", ""),
                        pair("10:41", "21:19"),
                        pair("10:28", "21:44"),
                        pair("10:55", "21:08"),
                        pair("10:33", "22:01"),
                        pair("10:19", "21:26"),
                        pair("21:05", ""),
                        pair("", ""),
                        pair("10:47", "21:38"),
                        pair("10:22", "21:15"),
                        pair("10:36", "21:52"),
                        pair("10:29", "21:07"),
                        pair("21:30", ""),
                        pair("10:34", "20:02"),
                        pair("", ""),
                        pair("10:22", "21:32"),
                        pair("10:30", "21:27"),
                        pair("10:28", "21:28"),
                        pair("10:31", "22:13"),
                        pair("10:49", "21:31"),
                        pair("10:33", "21:07"),
                        pair("", ""),
                        pair("10:27", "21:31"),
                        pair("10:32", "21:31"),
                        pair("10:37", "21:04"),
                        pair("10:27", "21:16"),
                        pair("10:31", "21:04"),
                        pair("10:32", "21:15"),
                        pair("", ""),
                        pair("10:36", "21:34")
                )),
                new EmployeeBlock("5", "Moinbhai", List.of(
                        pair("08:37", "17:22"),
                        pair("", ""),
                        pair("08:51", "17:41"),
                        pair("08:33", "17:18"),
                        pair("08:46", "17:55"),
                        pair("08:29", "17:12"),
                        pair("08:54", "17:38"),
                        pair("08:41", "13:05"),
                        pair("", ""),
                        pair("08:48", "17:27"),
                        pair("08:35", "17:44"),
                        pair("08:52", "17:19"),
                        pair("08:39", "17:36"),
                        pair("08:45", "17:30"),
                        pair("08:50", "17:28"),
                        pair("", ""),
                        pair("08:42", "17:35"),
                        pair("08:40", "17:31"),
                        pair("08:48", "17:29"),
                        pair("08:44", "17:40"),
                        pair("08:46", "17:33"),
                        pair("08:41", "17:27"),
                        pair("", ""),
                        pair("08:43", "17:32"),
                        pair("08:47", "17:36"),
                        pair("08:39", "17:30"),
                        pair("08:45", "17:34"),
                        pair("08:50", "17:28"),
                        pair("08:42", "17:31"),
                        pair("", ""),
                        pair("08:44", "17:29")
                ))
        );
    }

    private static String[] pair(String in, String out) {
        return new String[]{in, out};
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    record EmployeeBlock(String id, String name, List<String[]> days) {
    }
}
