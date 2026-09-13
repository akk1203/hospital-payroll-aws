package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.Employee;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EmployeeService {

    @Inject
    DynamoPayrollStore store;

    public List<Employee> list() {
        return store.listActiveEmployees();
    }

    public Employee get(String id) {
        return store.findEmployee(id).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
    }

    public Employee save(Employee employee) {
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new IllegalArgumentException("Employee name is required");
        }
        if (employee.getSalaryPerMonth() == null) {
            throw new IllegalArgumentException("Salary per month is required");
        }
        if (employee.getDateOfJoining() == null) {
            throw new IllegalArgumentException("Date of joining is required");
        }
        if (employee.getHoursPerDay() == null || employee.getHoursPerDay().signum() <= 0) {
            throw new IllegalArgumentException("Hours per day is required");
        }
        if (employee.getId() == null || employee.getId().isBlank()) {
            employee.setId(UUID.randomUUID().toString());
            employee.setActive(true);
        } else {
            Employee existing = get(employee.getId());
            employee.setActive(existing.isActive());
        }
        return store.saveEmployee(employee);
    }

    public void deactivate(String id) {
        Employee employee = get(id);
        employee.setActive(false);
        store.saveEmployee(employee);
    }
}
