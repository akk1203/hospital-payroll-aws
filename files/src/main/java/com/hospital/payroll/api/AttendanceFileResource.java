package com.hospital.payroll.api;

import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.service.AttendanceImportService;
import com.hospital.payroll.service.PrintedAttendanceReportFactory;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;

@Path("/api/attendance")
@Produces(MediaType.APPLICATION_JSON)
public class AttendanceFileResource {

    @Inject
    AttendanceImportService importService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    public AttendanceBatch upload(AttendanceUploadRequest request) {
        if (request == null || request.contentBase64 == null || request.contentBase64.isBlank()) {
            throw new IllegalArgumentException("Attendance file is required");
        }
        String payload = request.contentBase64;
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma > 0) {
            payload = payload.substring(comma + 1);
        }
        byte[] data = Base64.getDecoder().decode(payload);
        String filename = request.filename == null ? "attendance.csv" : request.filename;
        return importService.importFile(data, filename);
    }

    @GET
    @Path("/templates/printed")
    @Produces("application/octet-stream")
    public Response printed() throws Exception {
        byte[] bytes = PrintedAttendanceReportFactory.xlsxBytes();
        return Response.ok(bytes, "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=Attendance-Record-Report-Dharmik-Moin.xlsx")
                .build();
    }
}
