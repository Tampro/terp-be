package com.terp.erp.document.common;

import com.terp.erp.security.TenantGuard;

import java.util.Locale;

/** Small helpers shared by the document pages. */
public final class Texts {
    public static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    /** TODO(assumption): "today" (expiry, overdue acknowledgments) is the Vietnam date until companies carry a time zone. */
    public static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private Texts() {
    }

    public static java.time.LocalDate today(java.time.Clock clock) {
        return java.time.LocalDate.now(clock.withZone(ZONE));
    }

    public static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EDocumentError.DOC_001);
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
