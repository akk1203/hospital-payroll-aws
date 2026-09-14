package com.hospital.payroll.api;

import com.hospital.payroll.model.Payslip;
import com.hospital.payroll.service.MonthlyPayrollExcelExporter;
import com.hospital.payroll.service.PayrollService;
import com.hospital.payroll.service.PayslipExcelExporter;
import com.hospital.payroll.service.PayslipPdfExporter;
import com.hospital.payroll.service.WhatsAppPayslipService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.YearMonth;
import java.util.List;

@Path("/api/payroll")
@Produces(MediaType.APPLICATION_JSON)
public class PayrollExportResource {

    @Inject
    PayrollService payrollService;
    @Inject
    WhatsAppPayslipService whatsAppPayslipService;

    @GET
    @Path("/month/export")
    @Produces("application/octet-stream")
    public Response exportMonth(@QueryParam("month") String month) throws Exception {
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("Month is required");
        }
        YearMonth parsed = YearMonth.parse(month);
        List<Payslip> slips = payrollService.list(parsed);
        byte[] bytes = MonthlyPayrollExcelExporter.export(parsed.toString(), slips);
        return Response.ok(bytes, "application/octet-stream")
                .header("Content-Disposition",
                        "attachment; filename=\"" + MonthlyPayrollExcelExporter.filename(parsed.toString()) + "\"")
                .build();
    }

    @GET
    @Path("/{id}/export")
    @Produces("application/octet-stream")
    public Response export(@PathParam("id") String id) throws Exception {
        PayslipDetail detail = payrollService.detail(id);
        byte[] bytes = PayslipExcelExporter.export(detail);
        return Response.ok(bytes, "application/octet-stream")
                .header("Content-Disposition",
                        "attachment; filename=\"" + PayslipExcelExporter.filename(detail.getPayslip()) + "\"")
                .build();
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/octet-stream")
    public Response pdf(@PathParam("id") String id) throws Exception {
        Payslip slip = payrollService.get(id);
        byte[] bytes = PayslipPdfExporter.export(slip);
        return Response.ok(bytes, "application/octet-stream")
                .header("Content-Disposition",
                        "attachment; filename=\"" + PayslipPdfExporter.filename(slip) + "\"")
                .build();
    }

    @POST
    @Path("/{id}/whatsapp")
    public WhatsAppShareResponse whatsapp(@PathParam("id") String id, @Context HttpHeaders headers) throws Exception {
        return whatsAppPayslipService.share(id, publicApiBase(headers));
    }

    @GET
    @Path("/public-pdf/{token}")
    @Produces("application/octet-stream")
    public Response publicPdf(@PathParam("token") String token) {
        byte[] bytes = whatsAppPayslipService.downloadPublicPdf(token);
        return Response.ok(bytes, "application/octet-stream")
                .header("Content-Disposition", "inline; filename=\"salary-slip.pdf\"")
                .build();
    }

    static String publicApiBase(HttpHeaders headers) {
        String host = header(headers, "host");
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Could not determine the public API URL");
        }
        String proto = header(headers, "x-forwarded-proto");
        if (proto == null || proto.isBlank()) {
            proto = "https";
        }
        return proto + "://" + host;
    }

    private static String header(HttpHeaders headers, String name) {
        if (headers == null) {
            return null;
        }
        String value = headers.getHeaderString(name);
        return value == null ? null : value.trim();
    }
}
