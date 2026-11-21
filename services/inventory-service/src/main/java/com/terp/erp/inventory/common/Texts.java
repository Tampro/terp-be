package com.terp.erp.inventory.common;

import com.terp.erp.security.TenantGuard;

import java.util.Locale;

/** Small helpers shared by the inventory pages. */
public final class Texts {
    public static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private Texts() {
    }

    public static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EInventoryError.INV_001);
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String id(Long id) {
        return id == null ? null : id.toString();
    }

    /** LIKE pattern for a keyword, '' when none; ! escapes % and _. */
    public static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
