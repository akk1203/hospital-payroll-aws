package com.hospital.payroll.service;

import com.hospital.payroll.api.DayAdjustmentRequest;
import com.hospital.payroll.api.DayBreakdown;
import com.hospital.payroll.api.PayslipDetail;
import com.hospital.payroll.model.AttendanceBatch;
import com.hospital.payroll.model.AttendanceDay;
import com.hospital.payroll.model.AttendancePerson;
import com.hospital.payroll.model.DayCredit;
import com.hospital.payroll.model.DayStatus;
import com.hospital.payroll.model.Employee;
import com.hospital.payroll.aws.DynamoPayrollStore;
import com.hospital.payroll.model.Payslip;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class PayrollService {

    @Inject AttendanceService attendanceService;
    @Inject DynamoPayrollStore store;
    @Inject DayAdjustmentService dayAdjustmentService;

    public List<Payslip> calculate(String batchId, YearMonth month) {
        AttendanceBatch batch = attendanceService.get(batchId);
        dayAdjustmentService.applyToBatch(batch);
        attendanceService.save(batch);
        List<LocalDate> workingDates = workingDates(batch, month);
        if (workingDates.isEmpty()) {
            throw new IllegalArgumentException("This attendance file has no days in " + month);
        }

        List<Payslip> slips = new ArrayList<>();
        for (AttendancePerson person : batch.getPeople()) {
            if (!person.isMapped() || person.getMappedEmployeeId() == null) {
                continue;
            }
            Employee employee = store.findEmployee(person.getMappedEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Mapped employee missing: " + person.getMappedEmployeeName()));
            slips.add(buildPayslip(employee, person, workingDates, month, batchId));
        }
        store.replacePayslips(month.toString(), slips);
        return store.listPayslips(month.toString());
    }

    public List<Payslip> list(YearMonth month) {
        return store.listPayslips(month.toString());
    }

    public Payslip get(String id) {
        return store.findPayslip(id).orElseThrow(() -> new IllegalArgumentException("Payslip not found"));
    }

    public PayslipDetail detail(String payslipId) {
        Payslip payslip = get(payslipId);
        AttendanceBatch batch = attendanceService.get(payslip.getAttendanceBatchId());
        AttendancePerson person = findPerson(batch, payslip.getEmployeeId());
        dayAdjustmentService.applyToPerson(person, batch.getPeriodStart(), batch.getPeriodEnd());
        Employee employee = store.findEmployee(payslip.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        YearMonth month = YearMonth.parse(payslip.getMonth());
        List<LocalDate> dates = workingDates(batch, month);
        BigDecimal hoursPerDay = hoursPerDay(employee);
        BigDecimal hourlyRate = payslip.getHourlyRate() == null ? BigDecimal.ZERO : payslip.getHourlyRate();
        var adjustedDates = dayAdjustmentService.adjustedDates(payslip.getEmployeeId());

        PayslipDetail detail = new PayslipDetail();
        detail.setPayslip(payslip);
        List<DayBreakdown> days = new ArrayList<>();
        for (LocalDate date : dates) {
            AttendanceDay day = dayOn(person, date);
            DayStatus status = day == null ? DayStatus.ABSENT : day.getStatus();
            DayBreakdown row = new DayBreakdown();
            row.setDate(date);
            row.setWeekday(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            row.setTimeIn(day == null ? null : day.getTimeIn());
            row.setTimeOut(day == null ? null : day.getTimeOut());
            row.setStatus(status);
            row.setCredit(day == null || day.getCredit() == null ? DayCredit.WORKED : day.getCredit());
            BigDecimal punched = punchedHours(day);
            row.setPunchedHours(punched);
            BigDecimal credited = status == DayStatus.PRESENT ? hoursForDay(day, hoursPerDay) : BigDecimal.ZERO;
            row.setCreditedHours(credited.setScale(2, RoundingMode.HALF_UP));
            row.setPay(hourlyRate.multiply(credited).setScale(2, RoundingMode.HALF_UP));
            row.setAdjusted(adjustedDates.contains(date));
            row.setIncompletePunch(incompletePunch(day));
            days.add(row);
        }
        detail.setDays(days);
        return detail;
    }

    public PayslipDetail adjustDay(String payslipId, DayAdjustmentRequest request) {
        Payslip payslip = get(payslipId);
        if (request.getDate() == null || request.getDate().isBlank()) {
            throw new IllegalArgumentException("Date is required");
        }
        LocalDate date = LocalDate.parse(request.getDate());
        AttendanceBatch batch = attendanceService.get(payslip.getAttendanceBatchId());
        AttendancePerson person = findPerson(batch, payslip.getEmployeeId());
        AttendanceDay day = dayOn(person, date);
        if (day == null) {
            day = new AttendanceDay();
            day.setDate(date);
            day.setSourceEmployeeName(person.getSourceEmployeeName());
            day.setSourceEmployeeCode(person.getSourceEmployeeCode());
            day.setMappedEmployeeId(person.getMappedEmployeeId());
            day.setMappedEmployeeName(person.getMappedEmployeeName());
            person.getDays().add(day);
        }
        day.setTimeIn(blankToNull(request.getTimeIn()));
        day.setTimeOut(blankToNull(request.getTimeOut()));
        List<String> punches = new ArrayList<>();
        if (day.getTimeIn() != null) {
            punches.add(day.getTimeIn());
        }
        if (day.getTimeOut() != null) {
            punches.add(day.getTimeOut());
        }
        day.setPunches(punches);
        WorkedHoursCalculator.apply(day);
        DayCredit credit = request.getCredit() == null ? DayCredit.WORKED : request.getCredit();
        day.setCredit(credit);
        DayStatus status = request.getStatus();
        if (status == null) {
            if (credit == DayCredit.FULL_DAY || credit == DayCredit.HALF_DAY || !punches.isEmpty()) {
                status = DayStatus.PRESENT;
            } else {
                status = DayStatus.ABSENT;
            }
        }
        day.setStatus(status);
        attendanceService.save(batch);
        dayAdjustmentService.save(payslip.getEmployeeId(), date, day.getTimeIn(), day.getTimeOut(),
                day.getStatus(), day.getCredit());

        Employee employee = store.findEmployee(payslip.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        YearMonth month = YearMonth.parse(payslip.getMonth());
        Payslip rebuilt = buildPayslip(employee, person, workingDates(batch, month), month, batch.getId());
        rebuilt.setId(payslip.getId());
        store.savePayslip(rebuilt);
        return detail(payslip.getId());
    }

    private AttendancePerson findPerson(AttendanceBatch batch, String employeeId) {
        return batch.getPeople().stream()
                .filter(person -> employeeId.equals(person.getMappedEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Employee is not on this attendance file"));
    }

    private AttendanceDay dayOn(AttendancePerson person, LocalDate date) {
        return person.getDays().stream()
                .filter(item -> date.equals(item.getDate()))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal hoursPerDay(Employee employee) {
        if (employee.getHoursPerDay() == null || employee.getHoursPerDay().signum() <= 0) {
            return new BigDecimal("8");
        }
        return employee.getHoursPerDay();
    }

    private BigDecimal punchedHours(AttendanceDay day) {
        if (day == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (day.getWorkedHours() == null) {
            WorkedHoursCalculator.apply(day);
        }
        return day.getWorkedHours() == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : day.getWorkedHours();
    }

    private BigDecimal hoursForDay(AttendanceDay day, BigDecimal hoursPerDay) {
        DayCredit credit = day == null || day.getCredit() == null ? DayCredit.WORKED : day.getCredit();
        return switch (credit) {
            case FULL_DAY -> hoursPerDay;
            case HALF_DAY -> hoursPerDay.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            case WORKED -> punchedHours(day);
        };
    }

    private boolean incompletePunch(AttendanceDay day) {
        if (day == null) {
            return false;
        }
        boolean hasIn = hasText(day.getTimeIn());
        boolean hasOut = hasText(day.getTimeOut());
        if (hasIn != hasOut) {
            return true;
        }
        long punches = day.getPunches() == null ? 0
                : day.getPunches().stream().filter(this::hasText).count();
        return punches == 1 && !(hasIn && hasOut);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Payslip buildPayslip(Employee employee, AttendancePerson person, List<LocalDate> workingDates,
                                 YearMonth month, String batchId) {
        int present = 0;
        int leave = 0;
        int absent = 0;
        BigDecimal hoursPerDay = hoursPerDay(employee);
        BigDecimal workedHours = BigDecimal.ZERO;
        List<String> notes = new ArrayList<>();
        for (LocalDate date : workingDates) {
            AttendanceDay day = dayOn(person, date);
            DayStatus status = day == null ? DayStatus.ABSENT : day.getStatus();
            switch (status) {
                case PRESENT -> {
                    present++;
                    BigDecimal dayHours = hoursForDay(day, hoursPerDay);
                    workedHours = workedHours.add(dayHours);
                    DayCredit credit = day.getCredit() == null ? DayCredit.WORKED : day.getCredit();
                    String inOut = (day.getTimeIn() == null ? "-" : day.getTimeIn())
                            + "–" + (day.getTimeOut() == null ? "-" : day.getTimeOut());
                    notes.add(date + ": " + inOut + " " + credit + " (" + dayHours + "h)");
                    if (day.getNotes() != null && !day.getNotes().isBlank()) {
                        notes.add(date + " note: " + day.getNotes());
                    }
                }
                case LEAVE -> leave++;
                case ABSENT -> absent++;
            }
        }

        int allowed = Math.max(0, employee.getAllowedLeavesPerMonth());
        int sheetDays = workingDates.size();
        int payableDays = Math.max(0, sheetDays - allowed);
        int unpaidDays = Math.max(0, absent + leave - allowed);
        int paidLeaves = Math.min(allowed, leave + absent);
        BigDecimal salary = employee.getSalaryPerMonth() == null ? BigDecimal.ZERO : employee.getSalaryPerMonth();
        BigDecimal expectedHours = hoursPerDay.multiply(BigDecimal.valueOf(payableDays));
        BigDecimal hourlyRate = expectedHours.signum() == 0
                ? BigDecimal.ZERO
                : salary.divide(expectedHours, 4, RoundingMode.HALF_UP);
        BigDecimal paidLeaveHours = hoursPerDay.multiply(BigDecimal.valueOf(allowed));
        BigDecimal payableHours = workedHours;
        BigDecimal requiredPresentHours = hoursPerDay.multiply(BigDecimal.valueOf(present));
        BigDecimal overtimeHours = workedHours.subtract(requiredPresentHours).max(BigDecimal.ZERO);
        BigDecimal dailyRate = hourlyRate.multiply(hoursPerDay).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = hourlyRate.multiply(payableHours).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedPay = hourlyRate.multiply(expectedHours).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deduction = expectedPay.subtract(net).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Payslip payslip = new Payslip();
        payslip.setEmployeeId(employee.getId());
        payslip.setEmployeeName(employee.getName());
        payslip.setPosition(employee.getPosition());
        payslip.setAttendanceBatchId(batchId);
        payslip.setMonth(month.toString());
        payslip.setWorkingDays(workingDates.size());
        payslip.setPresentDays(present);
        payslip.setLeaveDays(leave);
        payslip.setAbsentDays(absent);
        payslip.setAllowedLeaves(allowed);
        payslip.setPaidLeaves(paidLeaves);
        payslip.setUnpaidDays(unpaidDays);
        payslip.setHoursPerDay(hoursPerDay);
        payslip.setExpectedHours(expectedHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setWorkedHours(workedHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setPaidLeaveHours(paidLeaveHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setPayableHours(payableHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setMonthlySalary(salary);
        payslip.setHourlyRate(hourlyRate.setScale(2, RoundingMode.HALF_UP));
        payslip.setDailyRate(dailyRate);
        payslip.setLeaveWithoutPayDeduction(deduction);
        payslip.setNetPay(net);
        payslip.setNotes(notes);
        return payslip;
    }

    private List<LocalDate> workingDates(AttendanceBatch batch, YearMonth month) {
        return batch.getPeople().stream()
                .flatMap(person -> person.getDays().stream())
                .map(AttendanceDay::getDate)
                .filter(date -> YearMonth.from(date).equals(month))
                .distinct()
                .sorted()
                .toList();
    }
}
