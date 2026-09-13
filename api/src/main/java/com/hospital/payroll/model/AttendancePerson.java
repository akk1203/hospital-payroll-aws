package com.hospital.payroll.model;

import java.util.ArrayList;
import java.util.List;

public class AttendancePerson {

    private String sourceEmployeeCode;
    private String sourceEmployeeName;
    private String mappedEmployeeId;
    private String mappedEmployeeName;
    private boolean mapped;
    private String matchReason;
    private List<AttendanceDay> days = new ArrayList<>();

    public String getSourceEmployeeCode() {
        return sourceEmployeeCode;
    }

    public void setSourceEmployeeCode(String sourceEmployeeCode) {
        this.sourceEmployeeCode = sourceEmployeeCode;
    }

    public String getSourceEmployeeName() {
        return sourceEmployeeName;
    }

    public void setSourceEmployeeName(String sourceEmployeeName) {
        this.sourceEmployeeName = sourceEmployeeName;
    }

    public String getMappedEmployeeId() {
        return mappedEmployeeId;
    }

    public void setMappedEmployeeId(String mappedEmployeeId) {
        this.mappedEmployeeId = mappedEmployeeId;
    }

    public String getMappedEmployeeName() {
        return mappedEmployeeName;
    }

    public void setMappedEmployeeName(String mappedEmployeeName) {
        this.mappedEmployeeName = mappedEmployeeName;
    }

    public boolean isMapped() {
        return mapped;
    }

    public void setMapped(boolean mapped) {
        this.mapped = mapped;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }

    public List<AttendanceDay> getDays() {
        return days;
    }

    public void setDays(List<AttendanceDay> days) {
        this.days = days;
    }

    public String getSourceKey() {
        return (sourceEmployeeCode == null ? "" : sourceEmployeeCode)
                + "|" + com.hospital.payroll.service.NameNormalizer.normalize(sourceEmployeeName);
    }
}
