package com.hospital.payroll.api;

public class WhatsAppShareResponse {
    public boolean skipped;
    public String reason;
    public String phone;
    public String whatsappUrl;
    public String employeeName;

    public static WhatsAppShareResponse skipped(String reason, String employeeName) {
        WhatsAppShareResponse response = new WhatsAppShareResponse();
        response.skipped = true;
        response.reason = reason;
        response.employeeName = employeeName;
        return response;
    }
}
