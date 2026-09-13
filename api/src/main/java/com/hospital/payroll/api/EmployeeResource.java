package com.hospital.payroll.api;

import com.hospital.payroll.model.Employee;
import com.hospital.payroll.service.EmployeeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    EmployeeService employeeService;

    @GET
    public List<Employee> list() {
        return employeeService.list();
    }

    @GET
    @Path("/{id}")
    public Employee get(@PathParam("id") String id) {
        return employeeService.get(id);
    }

    @POST
    public Response create(Employee employee) {
        if (employee != null) {
            employee.setId(null);
        }
        return Response.status(Response.Status.CREATED).entity(employeeService.save(employee)).build();
    }

    @PUT
    @Path("/{id}")
    public Employee update(@PathParam("id") String id, Employee employee) {
        employee.setId(id);
        return employeeService.save(employee);
    }

    @DELETE
    @Path("/{id}")
    public Response deactivate(@PathParam("id") String id) {
        employeeService.deactivate(id);
        return Response.noContent().build();
    }
}
