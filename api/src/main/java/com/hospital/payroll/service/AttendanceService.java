package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.Employee;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class AttendanceService {

    @Inject EmployeeMatcher matcher;
    @Inject DynamoPayrollStore store;
    @Inject DayAdjustmentService dayAdjustmentService;

    public AttendanceBatch mapPerson(String batchId, String sourceKey, String employeeId) {
        AttendanceBatch batch = get(batchId);
        AttendancePerson person = batch.getPeople().stream()
                .filter(item -> key(item).equals(sourceKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Person not found in this file"));
        matcher.saveMapping(person.getSourceEmployeeCode(), person.getSourceEmployeeName(), employeeId);
        person.setMappedEmployeeId(employeeId);
        applyMatches(batch);
        dayAdjustmentService.applyToBatch(batch);
        return store.saveBatch(batch);
    }

    public AttendanceBatch get(String id) {
        return store.findBatch(id).orElseThrow(() -> new IllegalArgumentException("Attendance file not found"));
    }

    public List<AttendanceBatch> list() {
        return store.listBatches();
    }

    public AttendanceBatch save(AttendanceBatch batch) {
        return store.saveBatch(batch);
    }

    public void applyMatches(AttendanceBatch batch) {
        int mapped = 0;
        for (AttendancePerson person : batch.getPeople()) {
            EmployeeMatcher.MatchResult result = matcher.match(person.getSourceEmployeeCode(), person.getSourceEmployeeName());
            if (person.getMappedEmployeeId() != null && result.employee() != null
                    && person.getMappedEmployeeId().equals(result.employee().getId())) {
                fill(person, result);
            } else if (result.isMatched()) {
                fill(person, result);
            } else if (person.getMappedEmployeeId() != null) {
                person.setMapped(true);
                person.setMatchReason("manual mapping");
            } else {
                person.setMapped(false);
                person.setMappedEmployeeName(null);
                person.setMatchReason(null);
            }
            if (person.isMapped()) {
                mapped++;
                person.getDays().forEach(day -> {
                    day.setMappedEmployeeId(person.getMappedEmployeeId());
                    day.setMappedEmployeeName(person.getMappedEmployeeName());
                });
            }
        }
        batch.setMappedPeople(mapped);
        batch.setUnmappedPeople(batch.getPeople().size() - mapped);
        batch.setTotalRows(batch.getPeople().stream().mapToInt(person -> person.getDays().size()).sum());
    }

    private void fill(AttendancePerson person, EmployeeMatcher.MatchResult result) {
        Employee employee = result.employee();
        person.setMapped(true);
        person.setMappedEmployeeId(employee.getId());
        person.setMappedEmployeeName(employee.getName());
        person.setMatchReason(result.reason());
    }

    public static String key(AttendancePerson person) {
        return (person.getSourceEmployeeCode() == null ? "" : person.getSourceEmployeeCode())
                + "|" + NameNormalizer.normalize(person.getSourceEmployeeName());
    }
}
