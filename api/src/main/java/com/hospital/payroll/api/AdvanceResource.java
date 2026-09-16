package com.hospital.payroll.api;

import com.hospital.payroll.model.Advance;
import com.hospital.payroll.service.AdvanceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/advances")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdvanceResource {

    @Inject
    AdvanceService advanceService;

    @GET
    public List<Advance> list(@QueryParam("month") String month) {
        return advanceService.list(month);
    }

    @GET
    @Path("/{id}")
    public Advance get(@PathParam("id") String id) {
        return advanceService.get(id);
    }

    @POST
    public Response create(Advance advance) {
        if (advance != null) {
            advance.setId(null);
        }
        return Response.status(Response.Status.CREATED).entity(advanceService.save(advance)).build();
    }

    @PUT
    @Path("/{id}")
    public Advance update(@PathParam("id") String id, Advance advance) {
        advance.setId(id);
        return advanceService.save(advance);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        advanceService.delete(id);
        return Response.noContent().build();
    }
}
