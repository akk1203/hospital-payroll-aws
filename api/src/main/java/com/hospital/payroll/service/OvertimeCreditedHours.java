package com.hospital.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounds punched hours to credited hours for overtime-eligible employees only.
 * Credited time is always a multiple of 15 minutes, with a grace band after the
 * scheduled day: the first 30 minutes stay at the schedule, 45 minutes credits
 * 30 extra minutes, and 50 minutes and above use 15-minute rounding.
 */
public final class OvertimeCreditedHours {

    private OvertimeCreditedHours() {
    }

    public static BigDecimal fromPunched(BigDecimal punchedHours, BigDecimal scheduledDayHours) {
        if (punchedHours == null || punchedHours.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (scheduledDayHours == null || scheduledDayHours.signum() <= 0) {
            return minutesToHours(roundToQuarter(toMinutes(punchedHours)));
        }
        int credited = creditMinutes(toMinutes(punchedHours), toMinutes(scheduledDayHours));
        return minutesToHours(credited);
    }

    static int creditMinutes(int punchedMinutes, int scheduledMinutes) {
        if (punchedMinutes <= 0) {
            return 0;
        }
        if (punchedMinutes <= scheduledMinutes + 30) {
            if (punchedMinutes <= scheduledMinutes) {
                return Math.min(roundToQuarter(punchedMinutes), scheduledMinutes);
            }
            return scheduledMinutes;
        }
        if (punchedMinutes < scheduledMinutes + 50) {
            if (punchedMinutes < scheduledMinutes + 45) {
                return scheduledMinutes;
            }
            return scheduledMinutes + 30;
        }
        int rounded = roundToQuarter(punchedMinutes);
        int nextHour = scheduledMinutes + 60;
        if (rounded < nextHour) {
            return nextHour;
        }
        return rounded;
    }

    static int roundToQuarter(int minutes) {
        int remainder = minutes % 15;
        int base = minutes - remainder;
        if (remainder <= 7) {
            return base;
        }
        return base + 15;
    }

    static int toMinutes(BigDecimal hours) {
        return hours.multiply(new BigDecimal("60")).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal minutesToHours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
    }
}
