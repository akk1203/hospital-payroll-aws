package com.hospital.payroll.api;

import com.hospital.payroll.model.Payslip;

import java.util.ArrayList;
import java.util.List;

public class PayslipDetail {
    private Payslip payslip;
    private List<DayBreakdown> days = new ArrayList<>();

    public Payslip getPayslip() {
        return payslip;
    }

    public void setPayslip(Payslip payslip) {
        this.payslip = payslip;
    }

    public List<DayBreakdown> getDays() {
        return days;
    }

    public void setDays(List<DayBreakdown> days) {
        this.days = days;
    }
}
