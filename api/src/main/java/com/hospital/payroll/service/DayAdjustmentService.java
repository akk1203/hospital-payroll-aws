package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.model.AttendanceDay;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.DayCredit;
import com.hospital.payroll.model.DayStatus;
import com.hospital.payroll.model.SavedDayAdjustment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DayAdjustmentService {

    @Inject
    DynamoPayrollStore store;

    public void save(String employeeId, LocalDate date, String timeIn, String timeOut,
                     DayStatus status, DayCredit credit) {
        SavedDayAdjustment saved = store.findAdjustment(employeeId, date)
                .orElseGet(SavedDayAdjustment::new);
        saved.setEmployeeId(employeeId);
        saved.setDate(date);
        saved.setTimeIn(timeIn);
        saved.setTimeOut(timeOut);
        saved.setStatus(status);
        saved.setCredit(credit == null ? DayCredit.WORKED : credit);
        saved.setUpdatedAt(Instant.now());
        store.saveAdjustment(saved);
    }

    public Set<LocalDate> adjustedDates(String employeeId) {
        if (employeeId == null) {
            return Set.of();
        }
        return store.listAdjustments(employeeId).stream()
                .map(SavedDayAdjustment::getDate)
                .filter(date -> date != null)
                .collect(Collectors.toSet());
    }

    public void applyToBatch(AttendanceBatch batch) {
        if (batch.getPeople() == null) {
            return;
        }
        for (AttendancePerson person : batch.getPeople()) {
            if (!person.isMapped() || person.getMappedEmployeeId() == null) {
                continue;
            }
            applyToPerson(person, batch.getPeriodStart(), batch.getPeriodEnd());
        }
    }

    public void applyToPerson(AttendancePerson person, LocalDate periodStart, LocalDate periodEnd) {
        if (person.getMappedEmployeeId() == null) {
            return;
        }
        for (SavedDayAdjustment saved : store.listAdjustments(person.getMappedEmployeeId())) {
            if (saved.getDate() == null) {
                continue;
            }
            if (periodStart != null && saved.getDate().isBefore(periodStart)) {
                continue;
            }
            if (periodEnd != null && saved.getDate().isAfter(periodEnd)) {
                continue;
            }
            if (person.getDays() == null) {
                person.setDays(new ArrayList<>());
            }
            AttendanceDay day = person.getDays().stream()
                    .filter(item -> saved.getDate().equals(item.getDate()))
                    .findFirst()
                    .orElse(null);
            if (day == null) {
                day = new AttendanceDay();
                day.setDate(saved.getDate());
                day.setSourceEmployeeName(person.getSourceEmployeeName());
                day.setSourceEmployeeCode(person.getSourceEmployeeCode());
                day.setMappedEmployeeId(person.getMappedEmployeeId());
                day.setMappedEmployeeName(person.getMappedEmployeeName());
                person.getDays().add(day);
            }
            apply(day, saved);
        }
    }

    private void apply(AttendanceDay day, SavedDayAdjustment saved) {
        day.setTimeIn(saved.getTimeIn());
        day.setTimeOut(saved.getTimeOut());
        List<String> punches = new ArrayList<>();
        if (saved.getTimeIn() != null && !saved.getTimeIn().isBlank()) {
            punches.add(saved.getTimeIn());
        }
        if (saved.getTimeOut() != null && !saved.getTimeOut().isBlank()) {
            punches.add(saved.getTimeOut());
        }
        day.setPunches(punches);
        WorkedHoursCalculator.apply(day);
        day.setCredit(saved.getCredit() == null ? DayCredit.WORKED : saved.getCredit());
        day.setStatus(saved.getStatus() == null ? DayStatus.PRESENT : saved.getStatus());
        day.setNotes("Kept from UI adjustment");
    }
}
