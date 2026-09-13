package com.hospital.payroll.api;

import com.hospital.payroll.model.DayCredit;
import com.hospital.payroll.model.DayStatus;

public class DayAdjustmentRequest {
    private String date;
    private String timeIn;
    private String timeOut;
    private DayStatus status;
    private DayCredit credit;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
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
}
