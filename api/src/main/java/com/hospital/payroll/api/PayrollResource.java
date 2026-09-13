package com.hospital.payroll.api;

import com.hospital.payroll.model.Payslip;
import com.hospital.payroll.service.PayrollService;
import com.hospital.payroll.service.PayslipExcelExporter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.YearMonth;
import java.util.List;

@Path("/api/payroll")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PayrollResource {

    @Inject
    PayrollService payrollService;

    @POST
    @Path("/calculate")
    public List<Payslip> calculate(@QueryParam("batchId") String batchId, @QueryParam("month") String month) {
        return payrollService.calculate(batchId, YearMonth.parse(month));
    }

    @GET
    public List<Payslip> list(@QueryParam("month") String month) {
        return payrollService.list(YearMonth.parse(month));
    }

    @GET
    @Path("/{id}")
    public PayslipDetail get(@PathParam("id") String id) {
        return payrollService.detail(id);
    }

    @GET
    @Path("/{id}/export")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response export(@PathParam("id") String id) throws Exception {
        PayslipDetail detail = payrollService.detail(id);
        byte[] bytes = PayslipExcelExporter.export(detail);
        return Response.ok(bytes)
                .header("Content-Disposition",
                        "attachment; filename=\"" + PayslipExcelExporter.filename(detail.getPayslip()) + "\"")
                .build();
    }

    @PUT
    @Path("/{id}/days")
    public PayslipDetail adjustDay(@PathParam("id") String id, DayAdjustmentRequest request) {
        return payrollService.adjustDay(id, request);
    }
}
