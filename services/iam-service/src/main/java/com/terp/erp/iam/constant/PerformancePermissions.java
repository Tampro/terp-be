package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by employee-service for §7.3; seeded in 20261120390000_performance_permission_init_data. */
public final class PerformancePermissions {
    public static final Set<String> ALL = Set.of("PERFORMANCE_VIEW", "PERFORMANCE_MANAGE");

    private PerformancePermissions() {
    }
}
