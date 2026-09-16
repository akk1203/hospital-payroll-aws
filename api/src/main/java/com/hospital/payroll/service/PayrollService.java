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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class PayrollService {

    @Inject AttendanceService attendanceService;
    @Inject DynamoPayrollStore store;
    @Inject DayAdjustmentService dayAdjustmentService;
    @Inject AdvanceService advanceService;

    public List<Payslip> calculate(String batchId, YearMonth month) {
        return calculate(List.of(batchId), month);
    }

    public List<Payslip> calculate(List<String> batchIds, YearMonth month) {
        List<String> ids = normalizeBatchIds(batchIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Select at least one attendance file");
        }
        List<AttendanceBatch> batches = new ArrayList<>();
        for (String id : ids) {
            AttendanceBatch batch = attendanceService.get(id);
            dayAdjustmentService.applyToBatch(batch);
            attendanceService.save(batch);
            batches.add(batch);
        }
        AttendanceBatch merged = mergeBatches(batches, month);
        List<LocalDate> workingDates = workingDates(merged, month);
        if (workingDates.isEmpty()) {
            throw new IllegalArgumentException("These attendance files have no days in " + month);
        }
        String batchKey = String.join(",", ids);
        List<Payslip> slips = new ArrayList<>();
        for (AttendancePerson person : merged.getPeople()) {
            if (!person.isMapped() || person.getMappedEmployeeId() == null) {
                continue;
            }
            Employee employee = store.findEmployee(person.getMappedEmployeeId())
                    .orElseThrow(() -> new IllegalArgumentException("Mapped employee missing: " + person.getMappedEmployeeName()));
            slips.add(buildPayslip(employee, person, workingDates, month, batchKey));
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
        List<AttendanceBatch> batches = loadBatches(payslip.getAttendanceBatchId());
        AttendancePerson person = findPersonAcross(batches, payslip.getEmployeeId());
        LocalDate periodStart = batches.stream().map(AttendanceBatch::getPeriodStart).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate periodEnd = batches.stream().map(AttendanceBatch::getPeriodEnd).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        dayAdjustmentService.applyToPerson(person, periodStart, periodEnd);
        Employee employee = store.findEmployee(payslip.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        YearMonth month = YearMonth.parse(payslip.getMonth());
        AttendanceBatch merged = new AttendanceBatch();
        merged.setPeople(List.of(person));
        List<LocalDate> dates = workingDates(merged, month);
        if (dates.isEmpty()) {
            dates = person.getDays().stream()
                    .map(AttendanceDay::getDate)
                    .filter(date -> date != null && YearMonth.from(date).equals(month))
                    .distinct()
                    .sorted()
                    .toList();
        }
        BigDecimal hoursPerDay = hoursPerDay(employee);
        BigDecimal dailyRate = payslip.getDailyRate() == null ? BigDecimal.ZERO : payslip.getDailyRate();
        BigDecimal salary = payslip.getMonthlySalary() == null ? BigDecimal.ZERO : payslip.getMonthlySalary();
        BigDecimal scheduledHours = payslip.getExpectedHours() == null
                ? hoursPerDay.multiply(BigDecimal.valueOf(Math.max(0, month.lengthOfMonth() - Math.max(0, employee.getAllowedLeavesPerMonth()))))
                : payslip.getExpectedHours();
        var adjustedDates = dayAdjustmentService.adjustedDates(payslip.getEmployeeId());
        boolean overtime = employee.getOvertimeEligible();

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
            List<String> punches = day == null || day.getPunches() == null ? List.of() : day.getPunches();
            row.setPunches(punches);
            row.setMultiplePunches(day != null && (day.isMultiplePunches() || punches.size() > 2));
            BigDecimal punched = punchedHours(day);
            row.setPunchedHours(punched);
            boolean missingPunch = incompletePunch(day);
            BigDecimal credited = status == DayStatus.PRESENT ? hoursForDay(day, hoursPerDay, overtime) : BigDecimal.ZERO;
            row.setCreditedHours(credited.setScale(2, RoundingMode.HALF_UP));
            row.setPay(dayPay(overtime, status, day, hoursPerDay, dailyRate, credited, salary, scheduledHours)
                    .setScale(2, RoundingMode.HALF_UP));
            row.setAdjusted(adjustedDates.contains(date));
            row.setIncompletePunch(missingPunch);
            boolean halfDay = day != null && day.getCredit() == DayCredit.HALF_DAY;
            row.setShortHours(status == DayStatus.PRESENT && !missingPunch && !halfDay
                    && punched.compareTo(hoursPerDay) < 0);
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
        List<AttendanceBatch> batches = loadBatches(payslip.getAttendanceBatchId());
        AttendanceBatch batch = batches.get(0);
        AttendancePerson person = null;
        AttendanceDay day = null;
        for (AttendanceBatch candidate : batches) {
            AttendancePerson found = findPersonOptional(candidate, payslip.getEmployeeId());
            if (found == null) {
                continue;
            }
            AttendanceDay foundDay = dayOn(found, date);
            if (foundDay != null) {
                batch = candidate;
                person = found;
                day = foundDay;
                break;
            }
            if (person == null) {
                batch = candidate;
                person = found;
            }
        }
        if (person == null) {
            throw new IllegalArgumentException("Employee is not on these attendance files");
        }
        if (day == null) {
            day = new AttendanceDay();
            day.setDate(date);
            day.setSourceEmployeeName(person.getSourceEmployeeName());
            day.setSourceEmployeeCode(person.getSourceEmployeeCode());
            day.setMappedEmployeeId(person.getMappedEmployeeId());
            day.setMappedEmployeeName(person.getMappedEmployeeName());
            person.getDays().add(day);
        }
        List<String> originalPunches = day.getPunches() == null ? new ArrayList<>() : new ArrayList<>(day.getPunches());
        day.setTimeIn(blankToNull(request.getTimeIn()));
        day.setTimeOut(blankToNull(request.getTimeOut()));
        if (originalPunches.size() > 2) {
            day.setPunches(originalPunches);
            day.setMultiplePunches(true);
            day.setWorkedHours(WorkedHoursCalculator.hoursBetween(day.getTimeIn(), day.getTimeOut()));
        } else {
            List<String> punches = new ArrayList<>();
            if (day.getTimeIn() != null) {
                punches.add(day.getTimeIn());
            }
            if (day.getTimeOut() != null) {
                punches.add(day.getTimeOut());
            }
            day.setPunches(punches);
            WorkedHoursCalculator.apply(day);
        }
        DayCredit credit = request.getCredit() == null ? DayCredit.WORKED : request.getCredit();
        day.setCredit(credit);
        DayStatus status = request.getStatus();
        if (status == null) {
            if (credit == DayCredit.FULL_DAY || credit == DayCredit.HALF_DAY
                    || day.getTimeIn() != null || day.getTimeOut() != null) {
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
        AttendanceBatch merged = mergeBatches(batches, month);
        AttendancePerson mergedPerson = findPerson(merged, payslip.getEmployeeId());
        Payslip rebuilt = buildPayslip(employee, mergedPerson, workingDates(merged, month), month, payslip.getAttendanceBatchId());
        rebuilt.setId(payslip.getId());
        store.savePayslip(rebuilt);
        return detail(payslip.getId());
    }

    private List<String> normalizeBatchIds(List<String> batchIds) {
        if (batchIds == null) {
            return List.of();
        }
        return batchIds.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
    }

    private List<AttendanceBatch> loadBatches(String batchIdField) {
        List<String> ids = normalizeBatchIds(batchIdField == null ? List.of() : List.of(batchIdField));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Attendance file not found");
        }
        return ids.stream().map(attendanceService::get).collect(Collectors.toCollection(ArrayList::new));
    }

    private AttendanceBatch mergeBatches(List<AttendanceBatch> batches, YearMonth month) {
        Map<String, AttendancePerson> byEmployee = new LinkedHashMap<>();
        LocalDate start = null;
        LocalDate end = null;
        for (AttendanceBatch batch : batches) {
            start = minDate(start, batch.getPeriodStart());
            end = maxDate(end, batch.getPeriodEnd());
            if (batch.getPeople() == null) {
                continue;
            }
            for (AttendancePerson person : batch.getPeople()) {
                if (!person.isMapped() || person.getMappedEmployeeId() == null) {
                    continue;
                }
                AttendancePerson target = byEmployee.computeIfAbsent(person.getMappedEmployeeId(), id -> {
                    AttendancePerson copy = new AttendancePerson();
                    copy.setMapped(true);
                    copy.setMappedEmployeeId(person.getMappedEmployeeId());
                    copy.setMappedEmployeeName(person.getMappedEmployeeName());
                    copy.setSourceEmployeeCode(person.getSourceEmployeeCode());
                    copy.setSourceEmployeeName(person.getSourceEmployeeName());
                    copy.setMatchReason(person.getMatchReason());
                    copy.setDays(new ArrayList<>());
                    return copy;
                });
                for (AttendanceDay day : person.getDays()) {
                    if (day.getDate() == null || !YearMonth.from(day.getDate()).equals(month)) {
                        continue;
                    }
                    AttendanceDay existing = dayOn(target, day.getDate());
                    if (existing == null) {
                        target.getDays().add(copyDay(day));
                    } else {
                        mergeDay(existing, day);
                    }
                }
            }
        }
        AttendanceBatch merged = new AttendanceBatch();
        merged.setPeriodStart(start);
        merged.setPeriodEnd(end);
        List<AttendancePerson> people = new ArrayList<>(byEmployee.values());
        people.sort(Comparator.comparing(p -> p.getMappedEmployeeName() == null ? "" : p.getMappedEmployeeName(),
                String.CASE_INSENSITIVE_ORDER));
        merged.setPeople(people);
        return merged;
    }

    private void mergeDay(AttendanceDay existing, AttendanceDay incoming) {
        List<String> merged = new ArrayList<>();
        for (List<String> source : List.of(
                existing.getPunches() == null ? List.<String>of() : existing.getPunches(),
                incoming.getPunches() == null ? List.<String>of() : incoming.getPunches())) {
            for (String punch : source) {
                if (punch == null || punch.isBlank()) {
                    continue;
                }
                String normalized;
                try {
                    normalized = WorkedHoursCalculator.normalizeTime(punch);
                } catch (Exception ignored) {
                    normalized = punch.trim();
                }
                if (!merged.contains(normalized)) {
                    merged.add(normalized);
                }
            }
        }
        if (merged.isEmpty()) {
            if (incoming.getStatus() != null && existing.getStatus() == DayStatus.ABSENT) {
                existing.setStatus(incoming.getStatus());
            }
            return;
        }
        existing.setPunches(merged);
        existing.setTimeIn(merged.get(0));
        existing.setTimeOut(merged.size() >= 2 ? merged.get(merged.size() - 1) : existing.getTimeOut());
        existing.setMultiplePunches(merged.size() > 2);
        existing.setWorkedHours(WorkedHoursCalculator.hoursBetween(existing.getTimeIn(), existing.getTimeOut()));
        if (incoming.getStatus() != null && (existing.getStatus() == null || existing.getStatus() == DayStatus.ABSENT)) {
            existing.setStatus(incoming.getStatus());
        } else if (existing.getStatus() == null) {
            existing.setStatus(DayStatus.PRESENT);
        }
        if (incoming.getNotes() != null && (existing.getNotes() == null || existing.getNotes().isBlank())) {
            existing.setNotes(incoming.getNotes());
        }
        if (incoming.getCredit() != null && existing.getCredit() == null) {
            existing.setCredit(incoming.getCredit());
        }
    }

    private AttendanceDay copyDay(AttendanceDay day) {
        AttendanceDay copy = new AttendanceDay();
        copy.setDate(day.getDate());
        copy.setPunches(day.getPunches() == null ? new ArrayList<>() : new ArrayList<>(day.getPunches()));
        copy.setTimeIn(day.getTimeIn());
        copy.setTimeOut(day.getTimeOut());
        copy.setWorkedHours(day.getWorkedHours());
        copy.setStatus(day.getStatus());
        copy.setCredit(day.getCredit());
        copy.setNotes(day.getNotes());
        copy.setMultiplePunches(day.isMultiplePunches() || copy.getPunches().size() > 2);
        copy.setSourceEmployeeCode(day.getSourceEmployeeCode());
        copy.setSourceEmployeeName(day.getSourceEmployeeName());
        copy.setMappedEmployeeId(day.getMappedEmployeeId());
        copy.setMappedEmployeeName(day.getMappedEmployeeName());
        return copy;
    }

    private AttendancePerson findPersonAcross(List<AttendanceBatch> batches, String employeeId) {
        AttendancePerson target = null;
        for (AttendanceBatch batch : batches) {
            if (batch.getPeople() == null) {
                continue;
            }
            for (AttendancePerson person : batch.getPeople()) {
                if (!employeeId.equals(person.getMappedEmployeeId())) {
                    continue;
                }
                if (target == null) {
                    target = new AttendancePerson();
                    target.setMapped(true);
                    target.setMappedEmployeeId(person.getMappedEmployeeId());
                    target.setMappedEmployeeName(person.getMappedEmployeeName());
                    target.setSourceEmployeeCode(person.getSourceEmployeeCode());
                    target.setSourceEmployeeName(person.getSourceEmployeeName());
                    target.setDays(new ArrayList<>());
                }
                for (AttendanceDay day : person.getDays()) {
                    if (day.getDate() == null) {
                        continue;
                    }
                    AttendanceDay existing = dayOn(target, day.getDate());
                    if (existing == null) {
                        target.getDays().add(copyDay(day));
                    } else {
                        mergeDay(existing, day);
                    }
                }
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Employee is not on these attendance files");
        }
        return target;
    }

    private AttendancePerson findPersonOptional(AttendanceBatch batch, String employeeId) {
        return batch.getPeople().stream()
                .filter(person -> employeeId.equals(person.getMappedEmployeeId()))
                .findFirst()
                .orElse(null);
    }

    private LocalDate minDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
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

    private BigDecimal hoursForDay(AttendanceDay day, BigDecimal hoursPerDay, boolean overtimeEligible) {
        if (incompletePunch(day)) {
            return hoursPerDay;
        }
        DayCredit credit = day == null || day.getCredit() == null ? DayCredit.WORKED : day.getCredit();
        return switch (credit) {
            case FULL_DAY -> hoursPerDay;
            case HALF_DAY -> hoursPerDay.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            case WORKED -> overtimeEligible
                    ? OvertimeCreditedHours.fromPunched(punchedHours(day), hoursPerDay)
                    : hoursPerDay;
        };
    }

    private BigDecimal dayPay(boolean overtimeEligible, DayStatus status, AttendanceDay day,
                              BigDecimal hoursPerDay, BigDecimal dailyRate, BigDecimal creditedHours,
                              BigDecimal monthlySalary, BigDecimal scheduledHours) {
        if (status != DayStatus.PRESENT) {
            return BigDecimal.ZERO;
        }
        if (overtimeEligible) {
            if (scheduledHours == null || scheduledHours.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal scheduledPortion = creditedHours.min(hoursPerDay);
            return monthlySalary.divide(scheduledHours, 4, RoundingMode.HALF_UP).multiply(scheduledPortion);
        }
        DayCredit credit = day == null || day.getCredit() == null ? DayCredit.WORKED : day.getCredit();
        if (credit == DayCredit.HALF_DAY && !incompletePunch(day)) {
            return dailyRate.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        }
        return dailyRate;
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
        boolean overtimeEligible = employee.getOvertimeEligible();
        BigDecimal workedHours = BigDecimal.ZERO;
        BigDecimal payableDayUnits = BigDecimal.ZERO;
        List<String> notes = new ArrayList<>();
        for (LocalDate date : workingDates) {
            AttendanceDay day = dayOn(person, date);
            DayStatus status = day == null ? DayStatus.ABSENT : day.getStatus();
            switch (status) {
                case PRESENT -> {
                    present++;
                    BigDecimal dayHours = hoursForDay(day, hoursPerDay, overtimeEligible);
                    workedHours = workedHours.add(dayHours);
                    DayCredit credit = day == null || day.getCredit() == null ? DayCredit.WORKED : day.getCredit();
                    if (credit == DayCredit.HALF_DAY && !incompletePunch(day)) {
                        payableDayUnits = payableDayUnits.add(new BigDecimal("0.5"));
                    } else {
                        payableDayUnits = payableDayUnits.add(BigDecimal.ONE);
                    }
                    String inOut = (day.getTimeIn() == null ? "-" : day.getTimeIn())
                            + "–" + (day.getTimeOut() == null ? "-" : day.getTimeOut());
                    notes.add(date + ": " + inOut + " " + credit + " (" + HoursFormat.hmSuffix(dayHours) + ")");
                    if (day.getNotes() != null && !day.getNotes().isBlank()) {
                        notes.add(date + " note: " + day.getNotes());
                    }
                    if (incompletePunch(day)) {
                        notes.add(date + ": missing in/out counted as full day");
                    }
                }
                case LEAVE -> leave++;
                case ABSENT -> absent++;
            }
        }

        // If absent fewer than 15 days, leave allowance is 0 for the month; otherwise use configured leaves.
        int configuredLeaves = Math.max(0, employee.getAllowedLeavesPerMonth());
        int allowed = absent < 15 ? 0 : configuredLeaves;
        if (absent < 15 && configuredLeaves > 0) {
            notes.add("Absent " + absent + " day(s) (< 15): leave allowance set to 0 for this month");
        }
        int unusedLeaves = Math.max(0, allowed - leave);
        int extraLeave = Math.max(0, leave - allowed);
        int unpaidDays = absent + extraLeave;
        int paidLeaves = Math.min(allowed, leave);
        BigDecimal salary = employee.getSalaryPerMonth() == null ? BigDecimal.ZERO : employee.getSalaryPerMonth();
        BigDecimal monthDays = BigDecimal.valueOf(month.lengthOfMonth());
        BigDecimal thirty = new BigDecimal("30");
        boolean overtime = overtimeEligible;
        BigDecimal dailyRate = overtime
                ? salary.divide(thirty, 4, RoundingMode.HALF_UP)
                : salary.divide(monthDays, 4, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = hoursPerDay.signum() == 0
                ? BigDecimal.ZERO
                : dailyRate.divide(hoursPerDay, 4, RoundingMode.HALF_UP);
        int scheduledDays = Math.max(0, month.lengthOfMonth() - allowed);
        BigDecimal scheduledHours = hoursPerDay.multiply(BigDecimal.valueOf(scheduledDays));
        BigDecimal paidLeaveHours = hoursPerDay.multiply(BigDecimal.valueOf(paidLeaves));
        BigDecimal payableHours = workedHours.add(paidLeaveHours);
        BigDecimal extraHours = overtime
                ? workedHours.subtract(scheduledHours).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal overtimeHours = overtime
                ? extraHours.setScale(2, RoundingMode.HALF_UP)
                : hoursPerDay.multiply(BigDecimal.valueOf(unusedLeaves)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtimePay = overtime
                ? hourlyRate.multiply(extraHours).setScale(2, RoundingMode.HALF_UP)
                : dailyRate.multiply(BigDecimal.valueOf(unusedLeaves)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payableDays = payableDayUnits.add(BigDecimal.valueOf(paidLeaves));
        BigDecimal basePay;
        if (overtime) {
            if (scheduledHours.signum() <= 0 || workedHours.compareTo(scheduledHours) >= 0) {
                basePay = salary.setScale(2, RoundingMode.HALF_UP);
            } else {
                basePay = salary.multiply(workedHours)
                        .divide(scheduledHours, 2, RoundingMode.HALF_UP);
            }
        } else {
            basePay = dailyRate.multiply(payableDays).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal gross = basePay.add(overtimePay).setScale(2, RoundingMode.HALF_UP);
        BigDecimal advance = advanceService.totalFor(employee.getId(), month.toString())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(advance).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedPay = salary.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deduction = expectedPay.subtract(gross).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

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
        payslip.setExpectedHours(scheduledHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setWorkedHours(workedHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setPaidLeaveHours(paidLeaveHours.setScale(2, RoundingMode.HALF_UP));
        payslip.setPayableHours(workedHours.add(paidLeaveHours).setScale(2, RoundingMode.HALF_UP));
        payslip.setOvertimeHours(overtimeHours);
        payslip.setMonthlySalary(salary);
        payslip.setHourlyRate(hourlyRate.setScale(2, RoundingMode.HALF_UP));
        payslip.setDailyRate(dailyRate.setScale(2, RoundingMode.HALF_UP));
        payslip.setLeaveWithoutPayDeduction(deduction);
        payslip.setAdvanceDeduction(advance);
        payslip.setNetPay(net);
        payslip.setOvertimePay(overtimePay);
        payslip.setOvertimeEligible(employee.getOvertimeEligible());
        payslip.setUnusedLeaveDays(unusedLeaves);
        payslip.setPayableDays(payableDays.setScale(2, RoundingMode.HALF_UP));
        if (advance.signum() > 0) {
            notes.add("Advance deducted: ₹ " + advance.toPlainString());
        }
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
