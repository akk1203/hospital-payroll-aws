package com.hospital.payroll.service;

import com.hospital.payroll.model.AttendanceDay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorkedHoursCalculator {

    private static final DateTimeFormatter H_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private WorkedHoursCalculator() {
    }

    public static void apply(AttendanceDay day) {
        List<String> punches = day.getPunches() == null ? List.of() : day.getPunches();
        if (!punches.isEmpty()) {
            if (day.getTimeIn() == null || day.getTimeIn().isBlank()) {
                day.setTimeIn(punches.get(0));
            }
            if (punches.size() >= 2 && (day.getTimeOut() == null || day.getTimeOut().isBlank())) {
                day.setTimeOut(punches.get(punches.size() - 1));
            }
        }
        day.setMultiplePunches(punches.size() > 2);
        day.setWorkedHours(hoursBetween(day.getTimeIn(), day.getTimeOut()));
    }

    /** Hours between selected in/out (first→last by default). */
    public static BigDecimal hoursBetween(String timeIn, String timeOut) {
        if (timeIn == null || timeIn.isBlank() || timeOut == null || timeOut.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return durationHours(timeIn, timeOut).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal hoursFrom(List<String> punches) {
        if (punches == null || punches.size() < 2) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return hoursBetween(punches.get(0), punches.get(punches.size() - 1));
    }

    public static List<String> combinePunches(String timeIn, String timeOut, String extraPunches) {
        List<String> punches = new ArrayList<>();
        addTimes(punches, timeIn);
        addTimes(punches, timeOut);
        addTimes(punches, extraPunches);
        return punches;
    }

    public static String normalizeTime(String text) {
        return parseTime(text).format(HH_MM);
    }

    private static void addTimes(List<String> punches, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String part : text.split("[;,]")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                punches.add(normalizeTime(trimmed));
            } catch (DateTimeParseException ignored) {
                // skip non-time tokens
            }
        }
    }

    static BigDecimal durationHours(String inText, String outText) {
        LocalTime in = parseTime(inText);
        LocalTime out = parseTime(outText);
        long minutes = ChronoUnit.MINUTES.between(in, out);
        if (minutes <= 0) {
            minutes += 24 * 60;
        }
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    static LocalTime parseTime(String text) {
        String value = text.trim().toUpperCase(Locale.ROOT).replace('.', ':');
        try {
            return LocalTime.parse(value, H_MM);
        } catch (DateTimeParseException ex) {
            return LocalTime.parse(value, HH_MM);
        }
    }
}
