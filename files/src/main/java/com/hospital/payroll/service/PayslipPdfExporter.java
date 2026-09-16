package com.hospital.payroll.service;

import com.hospital.payroll.HospitalProfile;
import com.hospital.payroll.model.Payslip;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PayslipPdfExporter {

    private static final Color TEAL = new Color(19, 78, 74);
    private static final Color LINE = new Color(217, 226, 236);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private PayslipPdfExporter() {
    }

    public static byte[] export(Payslip slip) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, TEAL);
            Font subFont = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(72, 101, 129));
            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(16, 42, 67));
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(16, 42, 67));
            Font moneyFont = new Font(Font.HELVETICA, 12, Font.BOLD, TEAL);
            Font small = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(72, 101, 129));

            Paragraph heading = new Paragraph(HospitalProfile.NAME, titleFont);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            Paragraph address = new Paragraph(HospitalProfile.ADDRESS, small);
            address.setAlignment(Element.ALIGN_CENTER);
            address.setSpacingBefore(4);
            document.add(address);
            Paragraph phone = new Paragraph("Phone: " + HospitalProfile.PHONE, small);
            phone.setAlignment(Element.ALIGN_CENTER);
            document.add(phone);
            Paragraph slipTitle = new Paragraph("Salary slip", subFont);
            slipTitle.setAlignment(Element.ALIGN_CENTER);
            slipTitle.setSpacingBefore(10);
            slipTitle.setSpacingAfter(16);
            document.add(slipTitle);

            PdfPTable identity = new PdfPTable(2);
            identity.setWidthPercentage(100);
            identity.setSpacingAfter(14);
            identity.setWidths(new float[] {1, 1});
            addInfo(identity, labelFont, valueFont, "Employee", slip.getEmployeeName());
            addInfo(identity, labelFont, valueFont, "Month", monthLabel(slip.getMonth()));
            addInfo(identity, labelFont, valueFont, "Position", blank(slip.getPosition()));
            addInfo(identity, labelFont, valueFont, "Pay type",
                    slip.getOvertimeEligible() ? "Hourly (overtime allowed)" : "Per day");
            document.add(identity);

            boolean showOvertimeHours = slip.getOvertimeEligible() && hasAmount(slip.getOvertimeHours());
            PdfPTable attendance = table(showOvertimeHours ? 4 : 3);
            headerCell(attendance, "Present days");
            headerCell(attendance, "Leave days");
            headerCell(attendance, "Absent days");
            if (showOvertimeHours) {
                headerCell(attendance, "Overtime hours");
            }
            valueCell(attendance, String.valueOf(slip.getPresentDays()));
            valueCell(attendance, String.valueOf(displayLeaveDays(slip)));
            valueCell(attendance, String.valueOf(displayAbsentDays(slip)));
            if (showOvertimeHours) {
                valueCell(attendance, HoursFormat.hm(slip.getOvertimeHours()));
            }
            document.add(attendance);

            PdfPTable pay = table(2);
            pay.setSpacingBefore(16);
            headerCell(pay, "Earnings / deductions");
            headerCell(pay, "Amount (INR)");
            addPayRow(pay, valueFont, slip.getOvertimeEligible()
                    ? "Scheduled hours (monthly salary)"
                    : "Gross monthly salary", inr(slip.getMonthlySalary()));
            if (slip.getOvertimeEligible() && hasAmount(slip.getOvertimeHours())) {
                addPayRow(pay, valueFont, "Overtime (" + HoursFormat.hmSuffix(slip.getOvertimeHours()) + ")", inr(slip.getOvertimePay()));
            }
            addPayRow(pay, valueFont, "Shortfall / unpaid days", inr(slip.getLeaveWithoutPayDeduction()));
            if (hasAmount(slip.getAdvanceDeduction())) {
                addPayRow(pay, valueFont, "Advance deduction", inr(slip.getAdvanceDeduction()));
            }
            PdfPCell netLabel = new PdfPCell(new Phrase("Net pay", moneyFont));
            netLabel.setPadding(8);
            netLabel.setBackgroundColor(new Color(204, 251, 241));
            netLabel.setBorderColor(LINE);
            PdfPCell netValue = new PdfPCell(new Phrase(inr(slip.getNetPay()), moneyFont));
            netValue.setPadding(8);
            netValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            netValue.setBackgroundColor(new Color(204, 251, 241));
            netValue.setBorderColor(LINE);
            pay.addCell(netLabel);
            pay.addCell(netValue);
            document.add(pay);

            Paragraph note = new Paragraph(
                    "Without overtime, daily rate is monthly salary ÷ days in the month. "
                            + "With overtime, scheduled hours ((days in the month − allowed leave) × hours/day) are paid as the monthly salary. "
                            + "Overtime credited hours are rounded to 15 minutes. "
                            + "Overtime is extra credited hours in the month above that schedule, paid at monthly salary ÷ 30 ÷ hours/day. "
                            + "Missing in or out is counted as a full day. This slip is generated from the calculated payroll.",
                    small);
            note.setSpacingBefore(18);
            document.add(note);

            document.close();
            return out.toByteArray();
        }
    }

    public static String filename(Payslip slip) {
        String name = slip.getEmployeeName() == null ? "employee" : slip.getEmployeeName()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        String month = slip.getMonth() == null ? "month" : slip.getMonth();
        return "SalarySlip-" + name + "-" + month + ".pdf";
    }

    private static String monthLabel(String month) {
        if (month == null || month.isBlank()) {
            return "";
        }
        try {
            return java.time.YearMonth.parse(month).format(MONTH);
        } catch (Exception ignored) {
            return month;
        }
    }

    private static PdfPTable table(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private static void addInfo(PdfPTable table, Font labelFont, Font valueFont, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorderColor(LINE);
        labelCell.setPadding(6);
        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "" : value, valueFont));
        valueCell.setBorderColor(LINE);
        valueCell.setPadding(6);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void headerCell(PdfPTable table, String text) {
        Font font = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(TEAL);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void valueCell(PdfPTable table, String text) {
        Font font = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(16, 42, 67));
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(LINE);
        table.addCell(cell);
    }

    private static void addPayRow(PdfPTable table, Font font, String label, String amount) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setPadding(7);
        labelCell.setBorderColor(LINE);
        PdfPCell amountCell = new PdfPCell(new Phrase(amount, font));
        amountCell.setPadding(7);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        amountCell.setBorderColor(LINE);
        table.addCell(labelCell);
        table.addCell(amountCell);
    }

    private static String inr(BigDecimal value) {
        if (value == null) {
            return "₹ 0.00";
        }
        return "₹ " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String text(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    static int displayLeaveDays(Payslip slip) {
        int allowed = Math.max(0, slip.getAllowedLeaves());
        int absent = Math.max(0, slip.getAbsentDays());
        if (absent >= allowed && allowed > 0) {
            return allowed;
        }
        return Math.max(0, slip.getLeaveDays());
    }

    static int displayAbsentDays(Payslip slip) {
        int allowed = Math.max(0, slip.getAllowedLeaves());
        int absent = Math.max(0, slip.getAbsentDays());
        if (absent >= allowed && allowed > 0) {
            return absent - allowed;
        }
        return absent;
    }

    private static boolean hasAmount(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
