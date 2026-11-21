package com.terp.erp.employee.performance;

import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.security.TenantGuard;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the performance pages. */
public final class PerformanceSupport {
    static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    /** All / Active / Inactive tabs. */
    public record StatusCounts(long all, Map<EOrgStatus, Long> byStatus) {
        static StatusCounts of(List<Object[]> rows) {
            Map<EOrgStatus, Long> by = new EnumMap<>(EOrgStatus.class);
            for (EOrgStatus s : EOrgStatus.values()) {
                by.put(s, 0L);
            }
            for (Object[] r : rows) {
                by.put((EOrgStatus) r[0], (Long) r[1]);
            }
            return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
        }
    }

    public record Ref(String id, String name) {
    }

    private PerformanceSupport() {
    }

    static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
