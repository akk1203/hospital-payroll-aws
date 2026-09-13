package com.hospital.payroll.model;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Payslip {

    private String id;
    private String employeeId;
    private String employeeName;
    private String position;
    private String attendanceBatchId;
    private String month;
    private int workingDays;
    private int presentDays;
    private int leaveDays;
    private int absentDays;
    private int allowedLeaves;
    private int paidLeaves;
    private int unpaidDays;
    private BigDecimal hoursPerDay;
    private BigDecimal expectedHours;
    private BigDecimal workedHours;
    private BigDecimal paidLeaveHours;
    private BigDecimal payableHours;
    private BigDecimal overtimeHours;
    private BigDecimal monthlySalary;
    private BigDecimal hourlyRate;
    private BigDecimal dailyRate;
    private BigDecimal leaveWithoutPayDeduction;
    private BigDecimal netPay;
    private BigDecimal overtimePay;
    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    private boolean overtimeEligible;
    private int unusedLeaveDays;
    private BigDecimal payableDays;
    private Instant calculatedAt = Instant.now();
    private List<String> notes = new ArrayList<>();

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

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getAttendanceBatchId() {
        return attendanceBatchId;
    }

    public void setAttendanceBatchId(String attendanceBatchId) {
        this.attendanceBatchId = attendanceBatchId;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public int getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(int workingDays) {
        this.workingDays = workingDays;
    }

    public int getPresentDays() {
        return presentDays;
    }

    public void setPresentDays(int presentDays) {
        this.presentDays = presentDays;
    }

    public int getLeaveDays() {
        return leaveDays;
    }

    public void setLeaveDays(int leaveDays) {
        this.leaveDays = leaveDays;
    }

    public int getAbsentDays() {
        return absentDays;
    }

    public void setAbsentDays(int absentDays) {
        this.absentDays = absentDays;
    }

    public int getAllowedLeaves() {
        return allowedLeaves;
    }

    public void setAllowedLeaves(int allowedLeaves) {
        this.allowedLeaves = allowedLeaves;
    }

    public int getPaidLeaves() {
        return paidLeaves;
    }

    public void setPaidLeaves(int paidLeaves) {
        this.paidLeaves = paidLeaves;
    }

    public int getUnpaidDays() {
        return unpaidDays;
    }

    public void setUnpaidDays(int unpaidDays) {
        this.unpaidDays = unpaidDays;
    }

    public BigDecimal getHoursPerDay() {
        return hoursPerDay;
    }

    public void setHoursPerDay(BigDecimal hoursPerDay) {
        this.hoursPerDay = hoursPerDay;
    }

    public BigDecimal getExpectedHours() {
        return expectedHours;
    }

    public void setExpectedHours(BigDecimal expectedHours) {
        this.expectedHours = expectedHours;
    }

    public BigDecimal getWorkedHours() {
        return workedHours;
    }

    public void setWorkedHours(BigDecimal workedHours) {
        this.workedHours = workedHours;
    }

    public BigDecimal getPaidLeaveHours() {
        return paidLeaveHours;
    }

    public void setPaidLeaveHours(BigDecimal paidLeaveHours) {
        this.paidLeaveHours = paidLeaveHours;
    }

    public BigDecimal getPayableHours() {
        return payableHours;
    }

    public void setPayableHours(BigDecimal payableHours) {
        this.payableHours = payableHours;
    }

    public BigDecimal getOvertimeHours() {
        return overtimeHours;
    }

    public void setOvertimeHours(BigDecimal overtimeHours) {
        this.overtimeHours = overtimeHours;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(BigDecimal monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public void setDailyRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    public BigDecimal getLeaveWithoutPayDeduction() {
        return leaveWithoutPayDeduction;
    }

    public void setLeaveWithoutPayDeduction(BigDecimal leaveWithoutPayDeduction) {
        this.leaveWithoutPayDeduction = leaveWithoutPayDeduction;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public BigDecimal getOvertimePay() {
        return overtimePay;
    }

    public void setOvertimePay(BigDecimal overtimePay) {
        this.overtimePay = overtimePay;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    public boolean getOvertimeEligible() {
        return overtimeEligible;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    public void setOvertimeEligible(boolean overtimeEligible) {
        this.overtimeEligible = overtimeEligible;
    }

    public int getUnusedLeaveDays() {
        return unusedLeaveDays;
    }

    public void setUnusedLeaveDays(int unusedLeaveDays) {
        this.unusedLeaveDays = unusedLeaveDays;
    }

    public BigDecimal getPayableDays() {
        return payableDays;
    }

    public void setPayableDays(BigDecimal payableDays) {
        this.payableDays = payableDays;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
