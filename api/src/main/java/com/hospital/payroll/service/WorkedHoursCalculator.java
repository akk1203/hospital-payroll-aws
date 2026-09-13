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
            day.setTimeIn(punches.get(0));
            if (punches.size() >= 2) {
                day.setTimeOut(punches.get(punches.size() - 1));
            }
        }
        day.setWorkedHours(hoursFrom(punches));
    }

    public static BigDecimal hoursFrom(List<String> punches) {
        if (punches == null || punches.size() < 2) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i + 1 < punches.size(); i += 2) {
            total = total.add(durationHours(punches.get(i), punches.get(i + 1)));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static List<String> combinePunches(String timeIn, String timeOut, String extraPunches) {
        List<String> punches = new ArrayList<>();
        addTimes(punches, timeIn);
        addTimes(punches, timeOut);
        addTimes(punches, extraPunches);
        return punches;
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
                punches.add(parseTime(trimmed).format(HH_MM));
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
