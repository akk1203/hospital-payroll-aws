import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds July Att.log Excel from tools/july-punches.txt
 * Handwritten notes ignored — only times + L / HD / PRESENT.
 */
public class JulyAttLogBuilder {

    private static final Pattern TIME = Pattern.compile("([01]?\\d|2[0-3]):[0-5]\\d");
    private static final Pattern EMP = Pattern.compile("^EMP\\s+id=(\\d+)\\s+name=(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY = Pattern.compile("^d(\\d{1,2})=(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Path DATA = Path.of("tools", "july-punches.txt");
    private static final Path OUT = Path.of("C:\\Users\\akkat\\Downloads\\Attendance_July_2026_AttLog.xlsx");

    public static void main(String[] args) throws Exception {
        Map<Integer, Emp> people = load(DATA);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Att.log report");
            row(sheet, 0).createCell(0).setCellValue("Attendance Record Report");
            Row info = row(sheet, 2);
            info.createCell(0).setCellValue("Att. Time");
            info.createCell(2).setCellValue("2026-07-01 ~ 2026-07-31");
            info.createCell(9).setCellValue("Tabulation");
            info.createCell(11).setCellValue("2026-08-01");
            Row days = row(sheet, 3);
            for (int d = 1; d <= 31; d++) {
                days.createCell(d - 1).setCellValue(String.valueOf(d));
            }
            int r = 4;
            int filled = 0;
            int withPair = 0;
            for (Emp emp : people.values()) {
                Row idRow = row(sheet, r++);
                idRow.createCell(0).setCellValue("ID:");
                idRow.createCell(2).setCellValue(String.valueOf(emp.id));
                idRow.createCell(8).setCellValue("Name:");
                idRow.createCell(10).setCellValue(emp.name);
                idRow.createCell(18).setCellValue("Dept.:");
                idRow.createCell(20).setCellValue("Company");
                Row punchRow = row(sheet, r++);
                for (Map.Entry<Integer, String> e : emp.days.entrySet()) {
                    String cell = formatCell(e.getValue());
                    if (cell == null || cell.isBlank()) {
                        continue;
                    }
                    punchRow.createCell(e.getKey() - 1).setCellValue(cell);
                    filled++;
                    if (TIME.matcher(cell).results().count() >= 2) {
                        withPair++;
                    }
                }
            }
            for (int c = 0; c < 31; c++) {
                sheet.setColumnWidth(c, 11 * 256);
            }
            try (FileOutputStream out = new FileOutputStream(OUT.toFile())) {
                wb.write(out);
            }
            System.out.println("Wrote " + OUT);
            System.out.println("employees=" + people.size() + " dayCells=" + filled + " inOutPairs=" + withPair);
        }
    }

    private static Map<Integer, Emp> load(Path path) throws Exception {
        Map<Integer, Emp> people = new LinkedHashMap<>();
        Emp current = null;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Matcher emp = EMP.matcher(line);
                if (emp.matches()) {
                    current = new Emp(Integer.parseInt(emp.group(1)), emp.group(2).trim());
                    people.put(current.id, current);
                    continue;
                }
                Matcher day = DAY.matcher(line);
                if (day.matches() && current != null) {
                    int d = Integer.parseInt(day.group(1));
                    if (d >= 1 && d <= 31) {
                        current.days.put(d, day.group(2).trim());
                    }
                }
            }
        }
        return people;
    }

    private static String formatCell(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        String upper = text.toUpperCase();
        if (upper.equals("L") || upper.equals("LEAVE")) {
            return "L";
        }
        if (upper.equals("PRESENT") || upper.equals("P")) {
            return "PRESENT";
        }
        if (upper.equals("HD") || upper.equals("HALF DAY") || upper.equals("HALFDAY")) {
            return "HD";
        }
        boolean halfDay = upper.contains("HD");
        StringBuilder times = new StringBuilder();
        Matcher m = TIME.matcher(text);
        String prev = null;
        while (m.find()) {
            String t = normalize(m.group());
            if (t.equals(prev)) {
                continue;
            }
            times.append(t);
            prev = t;
        }
        if (times.length() == 0) {
            return halfDay ? "HD" : "";
        }
        return halfDay ? times + " HD" : times.toString();
    }

    private static String normalize(String t) {
        String[] p = t.split(":");
        return String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    private static Row row(Sheet sheet, int index) {
        Row existing = sheet.getRow(index);
        return existing != null ? existing : sheet.createRow(index);
    }

    static final class Emp {
        final int id;
        final String name;
        final Map<Integer, String> days = new TreeMap<>();

        Emp(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
