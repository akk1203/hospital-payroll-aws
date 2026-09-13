package com.hospital.payroll.api;

import com.hospital.payroll.service.CognitoAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    CognitoAuthService authService;

    @GET
    @Path("/config")
    public Map<String, Object> config() {
        return authService.config();
    }

    @POST
    @Path("/signup")
    public Map<String, String> signup(AuthRequest request) {
        var result = authService.signup(request == null ? null : request.name,
                request == null ? null : request.email,
                request == null ? null : request.password);
        return Map.of("token", result.token(), "email", nullToEmpty(result.email()), "name", nullToEmpty(result.name()));
    }

    @POST
    @Path("/login")
    public Map<String, String> login(AuthRequest request) {
        var result = authService.login(request == null ? null : request.email,
                request == null ? null : request.password);
        return Map.of("token", result.token(), "email", nullToEmpty(result.email()), "name", nullToEmpty(result.name()));
    }

    @GET
    @Path("/me")
    public Map<String, String> me(@HeaderParam("Authorization") String authorization) {
        var user = authService.fromAuthorization(authorization);
        return Map.of("email", nullToEmpty(user.email()), "name", nullToEmpty(user.name()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
