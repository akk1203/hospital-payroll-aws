package com.hospital.payroll.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class Advance {

    private String id;
    private String employeeId;
    private String employeeName;
    /** Payroll month this advance should be deducted from, e.g. 2026-07. */
    private String month;
    private LocalDate givenOn;
    private BigDecimal amount;
    private String note;
    private Instant createdAt = Instant.now();

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

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public LocalDate getGivenOn() {
        return givenOn;
    }

    public void setGivenOn(LocalDate givenOn) {
        this.givenOn = givenOn;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
