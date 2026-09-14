package com.hospital.payroll.service;

import com.hospital.payroll.HospitalProfile;
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
import java.util.List;

public final class MonthlyPayrollExcelExporter {

    private MonthlyPayrollExcelExporter() {
    }

    public static byte[] export(String month, List<Payslip> slips) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payroll " + month);
            CellStyle title = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            title.setFont(titleFont);

            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
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

            CellStyle total = workbook.createCellStyle();
            total.cloneStyleFrom(money);
            total.setFont(bold);

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            cell(titleRow, 0, HospitalProfile.NAME + " — payroll " + month, title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 14));
            Row addressRow = sheet.createRow(r++);
            cell(addressRow, 0, HospitalProfile.ADDRESS + " · Phone: " + HospitalProfile.PHONE, box);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 14));
            r++;

            String[] headers = {
                    "Employee", "Position", "Pay type", "Present", "Leave", "Absent",
                    "Payable days", "Payable hours", "Daily rate", "Hourly rate",
                    "Gross salary", "Unused leave OT days", "Overtime pay", "Shortfall", "Net pay"
            };
            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < headers.length; i++) {
                cell(headerRow, i, headers[i], header);
            }

            BigDecimal netTotal = BigDecimal.ZERO;
            for (Payslip slip : slips) {
                Row row = sheet.createRow(r++);
                cell(row, 0, slip.getEmployeeName(), box);
                cell(row, 1, slip.getPosition(), box);
                cell(row, 2, slip.getOvertimeEligible() ? "Hourly (OT allowed)" : "Per day", box);
                numeric(row, 3, BigDecimal.valueOf(slip.getPresentDays()), hours);
                numeric(row, 4, BigDecimal.valueOf(slip.getLeaveDays()), hours);
                numeric(row, 5, BigDecimal.valueOf(slip.getAbsentDays()), hours);
                numeric(row, 6, slip.getPayableDays(), hours);
                numeric(row, 7, slip.getPayableHours(), hours);
                numeric(row, 8, slip.getDailyRate(), money);
                numeric(row, 9, slip.getHourlyRate(), money);
                numeric(row, 10, slip.getMonthlySalary(), money);
                numeric(row, 11, BigDecimal.valueOf(slip.getUnusedLeaveDays()), hours);
                numeric(row, 12, slip.getOvertimePay(), money);
                numeric(row, 13, slip.getLeaveWithoutPayDeduction(), money);
                numeric(row, 14, slip.getNetPay(), money);
                if (slip.getNetPay() != null) {
                    netTotal = netTotal.add(slip.getNetPay());
                }
            }

            Row totalRow = sheet.createRow(r);
            cell(totalRow, 0, "Total", header);
            for (int i = 1; i < 14; i++) {
                cell(totalRow, i, "", header);
            }
            numeric(totalRow, 14, netTotal, total);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static String filename(String month) {
        return "Payroll-" + (month == null ? "month" : month) + ".xlsx";
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
}
