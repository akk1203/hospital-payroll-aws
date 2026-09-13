package com.hospital.payroll.service;

import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.AttendanceBatch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AttendanceImportService {

    @Inject AttendanceFileParser parser;
    @Inject AttendanceService attendanceService;
    @Inject DynamoPayrollStore store;
    @Inject DayAdjustmentService dayAdjustmentService;

    public AttendanceBatch importFile(byte[] data, String originalFilename) {
        AttendanceFileParser.ParsedAttendance parsed = parser.parse(data, originalFilename);
        AttendanceBatch batch = new AttendanceBatch();
        batch.setOriginalFilename(originalFilename);
        batch.setPeriodStart(parsed.periodStart());
        batch.setPeriodEnd(parsed.periodEnd());
        batch.setUploadedAt(Instant.now());
        batch.setPeople(parsed.people());
        attendanceService.applyMatches(batch);
        dayAdjustmentService.applyToBatch(batch);
        return store.saveBatch(batch);
    }
}
