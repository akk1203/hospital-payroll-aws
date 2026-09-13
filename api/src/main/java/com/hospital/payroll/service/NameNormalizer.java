package com.hospital.payroll.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NameNormalizer {

    private static final Pattern NON_LETTERS = Pattern.compile("[^A-Z0-9]");

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        upper = upper.replaceFirst("^DR\\.?\\s*", "");
        upper = NON_LETTERS.matcher(upper).replaceAll("");
        return upper;
    }
}
