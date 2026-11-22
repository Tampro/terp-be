package com.terp.erp.support.common;

import com.terp.erp.security.TenantGuard;

import java.util.Locale;

public final class Texts {
    private Texts() {
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Lower-cased trimmed keyword, or null: list queries skip the filter on null. */
    public static String kw(String keyword) {
        String k = blankToNull(keyword);
        return k == null ? null : k.toLowerCase(Locale.ROOT);
    }

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** The company a list or create acts on (TenantGuard: a super admin must name it). */
    public static Long company(Long requestedCompanyId) {
        return TenantGuard.resolveCompany(requestedCompanyId, ESupportError.SUP_001);
    }
}
