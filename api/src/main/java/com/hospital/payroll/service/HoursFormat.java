package com.hospital.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats decimal hours as hours and minutes, e.g. 8.5 → 8:30. */
public final class HoursFormat {

    private HoursFormat() {
    }

    public static String hm(BigDecimal hours) {
        if (hours == null) {
            return "0:00";
        }
        BigDecimal absolute = hours.abs();
        int totalMinutes = absolute.multiply(BigDecimal.valueOf(60))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        String body = h + ":" + String.format("%02d", m);
        return hours.signum() < 0 ? "-" + body : body;
    }

    public static String hmSuffix(BigDecimal hours) {
        return hm(hours) + " h";
    }
}
