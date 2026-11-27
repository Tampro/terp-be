package com.terp.erp.store.common;

import com.terp.erp.security.TenantGuard;

import java.util.Locale;

/** Small helpers shared by the store pages. */
public final class Texts {
    public static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

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

    public static String id(Long id) {
        return id == null ? null : id.toString();
    }

    /** The company a list or create acts on (TenantGuard: a super admin must name it). */
    public static Long company(Long requestedCompanyId) {
        return TenantGuard.resolveCompany(requestedCompanyId, EStoreError.STO_007);
    }
}
