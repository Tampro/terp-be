package com.terp.erp.employee.training;

import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.security.TenantGuard;

/** Small helpers shared by the training pages. */
public final class TrainingSupport {
    static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    public record Ref(String id, String name) {
    }

    private TrainingSupport() {
    }

    static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
