package com.hospital.payroll.api;

import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.service.AttendanceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.List;

@Path("/api/attendance")
@Produces(MediaType.APPLICATION_JSON)
public class AttendanceResource {

    @Inject
    AttendanceService attendanceService;

    @GET
    public List<AttendanceBatch> list() {
        return attendanceService.list();
    }

    @GET
    @Path("/{id}")
    public AttendanceBatch get(@PathParam("id") String id) {
        return attendanceService.get(id);
    }

    @POST
    @Path("/{id}/map")
    public AttendanceBatch map(@PathParam("id") String id,
                               @QueryParam("sourceKey") String sourceKey,
                               @QueryParam("employeeId") String employeeId) {
        return attendanceService.mapPerson(id, sourceKey, employeeId);
    }

    @GET
    @Path("/templates/sample")
    @Produces("text/csv")
    public Response sample() throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("samples/attendance-sample.csv")) {
            if (in == null) {
                throw new IllegalArgumentException("Sample CSV is missing");
            }
            byte[] bytes = in.readAllBytes();
            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=attendance-sample.csv")
                    .build();
        }
    }
}
