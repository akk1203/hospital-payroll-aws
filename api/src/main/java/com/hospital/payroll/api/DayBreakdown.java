package com.hospital.payroll.api;

import com.hospital.payroll.model.DayCredit;
import com.hospital.payroll.model.DayStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DayBreakdown {
    private LocalDate date;
    private String weekday;
    private String timeIn;
    private String timeOut;
    private DayStatus status;
    private DayCredit credit;
    private BigDecimal punchedHours;
    private BigDecimal creditedHours;
    private BigDecimal pay;
    private boolean adjusted;
    private boolean incompletePunch;
    private boolean shortHours;
    private boolean multiplePunches;
    private java.util.List<String> punches = new java.util.ArrayList<>();

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getWeekday() {
        return weekday;
    }

    public void setWeekday(String weekday) {
        this.weekday = weekday;
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

    public BigDecimal getPunchedHours() {
        return punchedHours;
    }

    public void setPunchedHours(BigDecimal punchedHours) {
        this.punchedHours = punchedHours;
    }

    public BigDecimal getCreditedHours() {
        return creditedHours;
    }

    public void setCreditedHours(BigDecimal creditedHours) {
        this.creditedHours = creditedHours;
    }

    public BigDecimal getPay() {
        return pay;
    }

    public void setPay(BigDecimal pay) {
        this.pay = pay;
    }

    public boolean isAdjusted() {
        return adjusted;
    }

    public void setAdjusted(boolean adjusted) {
        this.adjusted = adjusted;
    }

    public boolean isIncompletePunch() {
        return incompletePunch;
    }

    public void setIncompletePunch(boolean incompletePunch) {
        this.incompletePunch = incompletePunch;
    }

    public boolean isShortHours() {
        return shortHours;
    }

    public void setShortHours(boolean shortHours) {
        this.shortHours = shortHours;
    }

    public boolean isMultiplePunches() {
        return multiplePunches;
    }

    public void setMultiplePunches(boolean multiplePunches) {
        this.multiplePunches = multiplePunches;
    }

    public java.util.List<String> getPunches() {
        return punches;
    }

    public void setPunches(java.util.List<String> punches) {
        this.punches = punches == null ? new java.util.ArrayList<>() : punches;
    }
}
