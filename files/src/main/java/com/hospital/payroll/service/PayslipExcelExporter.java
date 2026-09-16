package com.hospital.payroll.service;

import com.hospital.payroll.HospitalProfile;
import com.hospital.payroll.api.DayBreakdown;
import com.hospital.payroll.api.PayslipDetail;
import com.hospital.payroll.model.Payslip;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

public final class PayslipExcelExporter {

    private PayslipExcelExporter() {
    }

    public static byte[] export(PayslipDetail detail) throws IOException {
        Payslip slip = detail.getPayslip();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Daily breakdown");
            CellStyle title = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            title.setFont(titleFont);

            CellStyle label = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            label.setFont(bold);

            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setBorderTop(BorderStyle.THIN);
            header.setBorderLeft(BorderStyle.THIN);
            header.setBorderRight(BorderStyle.THIN);
            header.setAlignment(HorizontalAlignment.CENTER);

            CellStyle box = workbook.createCellStyle();
            box.setBorderBottom(BorderStyle.THIN);
            box.setBorderTop(BorderStyle.THIN);
            box.setBorderLeft(BorderStyle.THIN);
            box.setBorderRight(BorderStyle.THIN);

            CellStyle money = workbook.createCellStyle();
            money.cloneStyleFrom(box);
            money.setDataFormat(workbook.createDataFormat().getFormat("₹ #,##0.00"));

            CellStyle hours = workbook.createCellStyle();
            hours.cloneStyleFrom(box);
            hours.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

            CellStyle adjustedBox = filled(workbook, box, IndexedColors.LIGHT_TURQUOISE);
            CellStyle incompleteBox = filled(workbook, box, IndexedColors.LIGHT_YELLOW);
            CellStyle bothBox = filled(workbook, box, IndexedColors.GOLD);
            CellStyle absentBox = filled(workbook, box, IndexedColors.ROSE);
            CellStyle shortBox = filled(workbook, box, IndexedColors.LIGHT_ORANGE);
            CellStyle adjustedHours = filled(workbook, hours, IndexedColors.LIGHT_TURQUOISE);
            CellStyle incompleteHours = filled(workbook, hours, IndexedColors.LIGHT_YELLOW);
            CellStyle bothHours = filled(workbook, hours, IndexedColors.GOLD);
            CellStyle absentHours = filled(workbook, hours, IndexedColors.ROSE);
            CellStyle shortHours = filled(workbook, hours, IndexedColors.LIGHT_ORANGE);
            CellStyle adjustedMoney = filled(workbook, money, IndexedColors.LIGHT_TURQUOISE);
            CellStyle incompleteMoney = filled(workbook, money, IndexedColors.LIGHT_YELLOW);
            CellStyle bothMoney = filled(workbook, money, IndexedColors.GOLD);
            CellStyle absentMoney = filled(workbook, money, IndexedColors.ROSE);
            CellStyle shortMoney = filled(workbook, money, IndexedColors.LIGHT_ORANGE);

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            cell(titleRow, 0, HospitalProfile.NAME, title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
            info(sheet, r++, label, "Address", HospitalProfile.ADDRESS);
            info(sheet, r++, label, "Phone", HospitalProfile.PHONE);
            info(sheet, r++, label, "Document", "Employee daily salary breakdown");

            r++;
            info(sheet, r++, label, "Employee", slip.getEmployeeName());
            info(sheet, r++, label, "Position", slip.getPosition());
            info(sheet, r++, label, "Month", slip.getMonth());
            info(sheet, r++, label, "Hours / day", HoursFormat.hm(slip.getHoursPerDay()));
            info(sheet, r++, label, "Allowed leaves", String.valueOf(slip.getAllowedLeaves()));
            info(sheet, r++, label, "Scheduled hours ((days in month − allowed leave) × hours/day)", HoursFormat.hm(slip.getExpectedHours()));
            info(sheet, r++, label, "Pay type", slip.getOvertimeEligible() ? "Hourly (overtime allowed)" : "Per day");
            if (slip.getOvertimeEligible() && slip.getOvertimeHours() != null && slip.getOvertimeHours().signum() > 0) {
                info(sheet, r++, label, "Overtime hours", HoursFormat.hm(slip.getOvertimeHours()));
                info(sheet, r++, label, "Overtime pay", text(slip.getOvertimePay()));
            }
            info(sheet, r++, label, "Present days", String.valueOf(slip.getPresentDays()));
            info(sheet, r++, label, "Leave days", String.valueOf(PayslipPdfExporter.displayLeaveDays(slip)));
            info(sheet, r++, label, "Absent days", String.valueOf(PayslipPdfExporter.displayAbsentDays(slip)));
            info(sheet, r++, label, "Hours worked", HoursFormat.hm(slip.getWorkedHours()));
            info(sheet, r++, label, "Payable hours", HoursFormat.hm(slip.getPayableHours()));
            info(sheet, r++, label, "Gross monthly salary", text(slip.getMonthlySalary()));
            info(sheet, r++, label, "Shortfall", text(slip.getLeaveWithoutPayDeduction()));
            if (slip.getAdvanceDeduction() != null && slip.getAdvanceDeduction().signum() > 0) {
                info(sheet, r++, label, "Advance deduction", text(slip.getAdvanceDeduction()));
            }
            info(sheet, r++, label, "Net pay", text(slip.getNetPay()));
            r++;

            Row headerRow = sheet.createRow(r++);
            String[] headers = {"Date", "Day", "Time in", "Time out", "Status", "Credit", "Punched hours", "Credited hours", "Pay (INR)", "Note"};
            for (int i = 0; i < headers.length; i++) {
                cell(headerRow, i, headers[i], header);
            }

            for (DayBreakdown day : detail.getDays()) {
                boolean absent = day.getStatus() != null && "ABSENT".equals(day.getStatus().name());
                boolean both = day.isAdjusted() && day.isIncompletePunch();
                CellStyle textStyle = both ? bothBox : day.isIncompletePunch() ? incompleteBox
                        : absent ? absentBox : day.isShortHours() ? shortBox
                        : day.isAdjusted() ? adjustedBox : box;
                CellStyle hourStyle = both ? bothHours : day.isIncompletePunch() ? incompleteHours
                        : absent ? absentHours : day.isShortHours() ? shortHours
                        : day.isAdjusted() ? adjustedHours : hours;
                CellStyle moneyStyle = both ? bothMoney : day.isIncompletePunch() ? incompleteMoney
                        : absent ? absentMoney : day.isShortHours() ? shortMoney
                        : day.isAdjusted() ? adjustedMoney : money;
                String note = both ? "Corrected; missing in or out counted as full day"
                        : day.isIncompletePunch() ? "Missing in or out counted as full day"
                        : day.getStatus() != null && "ABSENT".equals(day.getStatus().name()) ? "Absent"
                        : day.isShortHours() ? "Worked less than regular hours"
                        : day.isAdjusted() ? "Corrected" : "";
                Row row = sheet.createRow(r++);
                cell(row, 0, day.getDate() == null ? "" : day.getDate().toString(), textStyle);
                cell(row, 1, nullToEmpty(day.getWeekday()), textStyle);
                cell(row, 2, nullToEmpty(day.getTimeIn()), textStyle);
                cell(row, 3, nullToEmpty(day.getTimeOut()), textStyle);
                cell(row, 4, day.getStatus() == null ? "" : labelOf(day.getStatus().name()), textStyle);
                cell(row, 5, day.getCredit() == null ? "" : labelOf(day.getCredit().name()), textStyle);
                cell(row, 6, HoursFormat.hm(day.getPunchedHours()), hourStyle);
                cell(row, 7, HoursFormat.hm(day.getCreditedHours()), hourStyle);
                numeric(row, 8, day.getPay(), moneyStyle);
                cell(row, 9, note, textStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static String filename(Payslip slip) {
        String name = slip.getEmployeeName() == null ? "employee" : slip.getEmployeeName()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String month = slip.getMonth() == null ? "month" : slip.getMonth();
        return "Payslip-" + name + "-" + month + ".xlsx";
    }

    private static CellStyle filled(Workbook workbook, CellStyle base, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void info(Sheet sheet, int rowIndex, CellStyle labelStyle, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, label, labelStyle);
        cell(row, 1, value == null ? "" : value, null);
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void numeric(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? 0 : value.doubleValue());
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static String text(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String labelOf(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
