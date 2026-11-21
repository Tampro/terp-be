package com.terp.erp.recruitment.common;

import java.util.Locale;

public final class Texts {
    private Texts() {
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** LIKE pattern of a keyword, "!" being the escape character of the query: % and _ match themselves. */
    public static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
                + "%";
    }
}
