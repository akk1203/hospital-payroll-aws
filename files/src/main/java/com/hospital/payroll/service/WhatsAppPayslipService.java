package com.hospital.payroll.service;

import com.hospital.payroll.api.WhatsAppShareResponse;
import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.Employee;
import com.hospital.payroll.model.Payslip;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@ApplicationScoped
public class WhatsAppPayslipService {

    @Inject PayrollService payrollService;
    @Inject DynamoPayrollStore store;
    @Inject S3Client s3;

    @ConfigProperty(name = "payroll.aws.data-bucket")
    String dataBucket;

    public WhatsAppShareResponse share(String payslipId, String publicApiBase) throws Exception {
        Payslip slip = payrollService.get(payslipId);
        Employee employee = store.findEmployee(slip.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        String phone = internationalDigits(employee.getWhatsappNumber());
        if (phone == null) {
            return WhatsAppShareResponse.skipped("No WhatsApp number on the employee profile", slip.getEmployeeName());
        }

        String token = UUID.randomUUID().toString();
        byte[] pdf = PayslipPdfExporter.export(slip);
        s3.putObject(PutObjectRequest.builder()
                .bucket(dataBucket)
                .key(pdfKey(token))
                .contentType("application/pdf")
                .contentDisposition("inline; filename=\"" + PayslipPdfExporter.filename(slip) + "\"")
                .build(), RequestBody.fromBytes(pdf));

        String pdfUrl = publicBase(publicApiBase) + "/api/payroll/public-pdf/" + token;
        String text = "Hello " + nullToEmpty(slip.getEmployeeName())
                + ", your salary slip for " + nullToEmpty(slip.getMonth())
                + " is ready. Net pay: INR " + (slip.getNetPay() == null ? "0" : slip.getNetPay().toPlainString())
                + ". Download: " + pdfUrl;

        WhatsAppShareResponse response = new WhatsAppShareResponse();
        response.skipped = false;
        response.phone = phone;
        response.employeeName = slip.getEmployeeName();
        response.whatsappUrl = "https://wa.me/" + phone + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        return response;
    }

    public byte[] downloadPublicPdf(String token) {
        if (token == null || !token.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Salary slip link is not valid");
        }
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(dataBucket)
                            .key(pdfKey(token))
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new IllegalArgumentException("Salary slip link has expired or was not found");
        }
    }

    static String pdfKey(String token) {
        return "payslips/" + token + ".pdf";
    }

    private static String publicBase(String publicApiBase) {
        if (publicApiBase == null || publicApiBase.isBlank()) {
            throw new IllegalStateException("Could not determine the public API URL");
        }
        return publicApiBase.replaceAll("/+$", "");
    }

    static String internationalDigits(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            digits = digits.replaceFirst("^0+", "");
        }
        if (digits.length() == 10) {
            digits = "91" + digits;
        }
        if (digits.length() < 11 || digits.length() > 15) {
            return null;
        }
        return digits;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
