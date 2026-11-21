package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by payroll-service for §8.1; seeded in 20261120460000_payroll_permission_init_data. */
public final class PayrollPermissions {
    public static final Set<String> ALL = Set.of("PAYROLL_VIEW", "PAYROLL_MANAGE");

    private PayrollPermissions() {
    }
}
