package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.Advance;
import com.hospital.payroll.model.Employee;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@ApplicationScoped
public class AdvanceService {

    @Inject DynamoPayrollStore store;

    public List<Advance> list(String month) {
        if (month == null || month.isBlank()) {
            return store.listAdvances();
        }
        YearMonth.parse(month);
        return store.listAdvancesByMonth(month);
    }

    public Advance get(String id) {
        return store.findAdvance(id).orElseThrow(() -> new IllegalArgumentException("Advance not found"));
    }

    public Advance save(Advance advance) {
        if (advance.getEmployeeId() == null || advance.getEmployeeId().isBlank()) {
            throw new IllegalArgumentException("Employee is required");
        }
        if (advance.getAmount() == null || advance.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Advance amount must be greater than zero");
        }
        if (advance.getGivenOn() == null) {
            advance.setGivenOn(LocalDate.now());
        }
        // Payroll month is always taken from the transaction date.
        advance.setMonth(YearMonth.from(advance.getGivenOn()).toString());
        Employee employee = store.findEmployee(advance.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        advance.setEmployeeName(employee.getName());
        if (advance.getId() != null && !advance.getId().isBlank()) {
            store.findAdvance(advance.getId()).ifPresent(existing -> {
                if (advance.getCreatedAt() == null) {
                    advance.setCreatedAt(existing.getCreatedAt());
                }
            });
        }
        if (advance.getCreatedAt() == null) {
            advance.setCreatedAt(Instant.now());
        }
        if (advance.getNote() == null) {
            advance.setNote("");
        }
        return store.saveAdvance(advance);
    }

    public void delete(String id) {
        store.deleteAdvance(id);
    }

    public BigDecimal totalFor(String employeeId, String month) {
        return store.listAdvancesByEmployeeAndMonth(employeeId, month).stream()
                .map(Advance::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
