package com.hospital.payroll.model;


import java.time.Instant;
import java.time.LocalDate;

public class SavedDayAdjustment {

    private String id;
    private String employeeId;
    private LocalDate date;
    private String timeIn;
    private String timeOut;
    private DayStatus status;
    private DayCredit credit;
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
