package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.Employee;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@ApplicationScoped
public class AttendanceImportService {

    @Inject AttendanceFileParser parser;
    @Inject AttendanceService attendanceService;
    @Inject DynamoPayrollStore store;
    @Inject DayAdjustmentService dayAdjustmentService;
    @Inject EmployeeService employeeService;
    @Inject EmployeeMatcher matcher;

    public AttendanceBatch importFile(byte[] data, String originalFilename) {
        AttendanceFileParser.ParsedAttendance parsed = parser.parse(data, originalFilename);
        AttendanceBatch batch = new AttendanceBatch();
        batch.setOriginalFilename(originalFilename);
        batch.setPeriodStart(parsed.periodStart());
        batch.setPeriodEnd(parsed.periodEnd());
        batch.setUploadedAt(Instant.now());
        batch.setPeople(parsed.people());
        attendanceService.applyMatches(batch);
        createMissingEmployees(batch);
        attendanceService.applyMatches(batch);
        dayAdjustmentService.applyToBatch(batch);
        return store.saveBatch(batch);
    }

    private void createMissingEmployees(AttendanceBatch batch) {
        if (batch.getPeople() == null) {
            return;
        }
        LocalDate joining = batch.getPeriodStart() == null ? LocalDate.now() : batch.getPeriodStart();
        for (AttendancePerson person : batch.getPeople()) {
            if (person.isMapped() && person.getMappedEmployeeId() != null) {
                continue;
            }
            String name = person.getSourceEmployeeName();
            if (name == null || name.isBlank()) {
                continue;
            }
            Employee employee = new Employee();
            employee.setName(name.trim());
            employee.setAttendanceCode(blank(person.getSourceEmployeeCode()));
            employee.setPosition("Staff");
            employee.setDepartment("");
            employee.setDateOfJoining(joining);
            employee.setSalaryPerMonth(BigDecimal.ZERO);
            employee.setHoursPerDay(new BigDecimal("8"));
            employee.setAllowedLeavesPerMonth(2);
            employee.setOvertimeEligible(false);
            employee.setWhatsappNumber("");
            employee.setActive(true);
            Employee saved = employeeService.save(employee);
            matcher.saveMapping(person.getSourceEmployeeCode(), person.getSourceEmployeeName(), saved.getId());
            person.setMapped(true);
            person.setMappedEmployeeId(saved.getId());
            person.setMappedEmployeeName(saved.getName());
            person.setMatchReason("created from attendance file");
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
