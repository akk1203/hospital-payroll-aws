package com.hospital.payroll.api;

import com.hospital.payroll.service.DemoResetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    @Inject
    DemoResetService demoResetService;

    @GET
    public Map<String, Object> status() {
        return demoResetService.status();
    }

    @DELETE
    @Path("/data")
    public Map<String, Object> reset() {
        return demoResetService.reset();
    }
}
