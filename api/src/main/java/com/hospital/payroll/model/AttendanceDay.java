package com.hospital.payroll.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AttendanceDay {

    private LocalDate date;
    private List<String> punches = new ArrayList<>();
    private String timeIn;
    private String timeOut;
    private BigDecimal workedHours;
    private DayStatus status = DayStatus.ABSENT;
    private DayCredit credit = DayCredit.WORKED;
    private String notes;
    private String sourceEmployeeName;
    private String sourceEmployeeCode;
    private String mappedEmployeeId;
    private String mappedEmployeeName;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<String> getPunches() {
        return punches;
    }

    public void setPunches(List<String> punches) {
        this.punches = punches;
    }

    public String getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(String timeIn) {
        this.timeIn = timeIn;
    }

    public String getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    public BigDecimal getWorkedHours() {
        return workedHours;
    }

    public void setWorkedHours(BigDecimal workedHours) {
        this.workedHours = workedHours;
    }

    public DayStatus getStatus() {
        return status;
    }

    public void setStatus(DayStatus status) {
        this.status = status;
    }

    public DayCredit getCredit() {
        return credit;
    }

    public void setCredit(DayCredit credit) {
        this.credit = credit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSourceEmployeeName() {
        return sourceEmployeeName;
    }

    public void setSourceEmployeeName(String sourceEmployeeName) {
        this.sourceEmployeeName = sourceEmployeeName;
    }

    public String getSourceEmployeeCode() {
        return sourceEmployeeCode;
    }

    public void setSourceEmployeeCode(String sourceEmployeeCode) {
        this.sourceEmployeeCode = sourceEmployeeCode;
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
}
