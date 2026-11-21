package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by employee-service; seeded in 20261120100000_employee_permission_init_data and
 * 20261120170000_announcement_permission_init_data.
 * EMPLOYEE_BANKING_VIEW guards bank account, tax payer id and base salary (PO, 2026-11-19: separate permission).
 */
public final class EmployeePermissions {
    public static final Set<String> ALL = Set.of("EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_UPDATE",
            "EMPLOYEE_DELETE", "EMPLOYEE_BANKING_VIEW", "ORG_VIEW", "ORG_MANAGE", "ANNOUNCEMENT_VIEW",
            "ANNOUNCEMENT_MANAGE");

    private EmployeePermissions() {
    }
}
