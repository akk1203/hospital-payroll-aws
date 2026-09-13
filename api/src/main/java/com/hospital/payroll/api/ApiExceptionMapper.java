package com.hospital.payroll.api;

import com.hospital.payroll.service.DemoResetNotAllowedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof WebApplicationException web) {
            return web.getResponse();
        }
        if (exception instanceof DemoResetNotAllowedException) {
            return Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", exception.getMessage()))
                    .build();
        }
        if (exception instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", exception.getMessage() == null ? "Bad request" : exception.getMessage()))
                    .build();
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage() == null ? "Server error" : exception.getMessage()))
                .build();
    }
}
