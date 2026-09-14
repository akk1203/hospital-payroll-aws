package com.hospital.payroll.model;


import java.math.BigDecimal;
import java.time.LocalDate;

public class Employee {

    private String id;

    private String name;

    private String position;

    private String department;

    private LocalDate dateOfJoining;

    private BigDecimal salaryPerMonth;

    private int allowedLeavesPerMonth;

    /**
     * Required duty hours for a working day.
     * Without overtime, daily rate is monthly salary ÷ days in that month.
     * With overtime, hourly rate is (monthly salary ÷ 30) ÷ this value.
     */
    private BigDecimal hoursPerDay;

    private String attendanceCode;

    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    private boolean overtimeEligible;

    private String whatsappNumber;

    private boolean active = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public BigDecimal getSalaryPerMonth() {
        return salaryPerMonth;
    }

    public void setSalaryPerMonth(BigDecimal salaryPerMonth) {
        this.salaryPerMonth = salaryPerMonth;
    }

    public int getAllowedLeavesPerMonth() {
        return allowedLeavesPerMonth;
    }

    public void setAllowedLeavesPerMonth(int allowedLeavesPerMonth) {
        this.allowedLeavesPerMonth = allowedLeavesPerMonth;
    }

    public BigDecimal getHoursPerDay() {
        return hoursPerDay;
    }

    public void setHoursPerDay(BigDecimal hoursPerDay) {
        this.hoursPerDay = hoursPerDay;
    }

    public String getAttendanceCode() {
        return attendanceCode;
    }

    public void setAttendanceCode(String attendanceCode) {
        this.attendanceCode = attendanceCode;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    public boolean getOvertimeEligible() {
        return overtimeEligible;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("overtimeEligible")
    public void setOvertimeEligible(boolean overtimeEligible) {
        this.overtimeEligible = overtimeEligible;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
