package com.terp.erp.iam.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Roles every company starts with: company admin, HR, manager, employee (staff), executive board, accountant, and the
 * portal roles CLIENT (a customer) and VENDOR (a supplier) of the ERP roles list (docs/erp/analysis/08 §1).
 * They are system roles: permissions can be edited, the code cannot change and the role cannot be deleted.
 * Each business module adds its codes here when it is built.
 */
@Getter
@RequiredArgsConstructor
public enum EDefaultRole {
    COMPANY_ADMIN("Company administrator",
            union(union(union(IamPermissions.ALL, com.terp.erp.iam.saas.SaasPermissions.ALL), MediaPermissions.ALL),
                    union(union(NotificationPermissions.ALL, EmployeePermissions.ALL),
                            union(union(AttendancePermissions.ALL, RecruitmentPermissions.ALL),
                                    union(LifecyclePermissions.ALL, union(PerformancePermissions.ALL,
                                            union(TrainingPermissions.ALL,
                                                    union(PayrollPermissions.ALL,
                                                            union(AssetPermissions.ALL,
                                                                    union(MeetingPermissions.ALL,
                                                                            union(EventPermissions.ALL,
                                                                                    union(ZoomPermissions.ALL, DocumentPermissions.ALL)))))))))))),
    // TODO(assumption): HR manages accounts but not roles, and sees banking data — confirm with BA.
    HR("HR", union(Set.of(IamPermissions.USER_VIEW, IamPermissions.USER_CREATE, IamPermissions.USER_UPDATE,
            IamPermissions.ROLE_VIEW, IamPermissions.PERMISSION_VIEW), union(union(MediaPermissions.ALL, EmployeePermissions.ALL),
            union(union(AttendancePermissions.ALL, RecruitmentPermissions.ALL),
                    union(LifecyclePermissions.ALL, union(PerformancePermissions.ALL,
                            union(TrainingPermissions.ALL,
                                    union(PayrollPermissions.ALL,
                                            union(AssetPermissions.ALL,
                                                    union(MeetingPermissions.ALL,
                                                            union(EventPermissions.ALL,
                                                                    union(ZoomPermissions.ALL, DocumentPermissions.ALL)))))))))))),
    MANAGER("Manager", Set.of()),
    EMPLOYEE("Employee", Set.of()),
    EXECUTIVE("Executive board", Set.of()),
    ACCOUNTANT("Accountant", Set.of()),
    // TODO(assumption): a client sees only its own invoices, payments, credit notes, deals and tickets, and a vendor
    // its own purchase invoices, payments and debit notes (docs/erp/analysis/07 §1). Those permissions come with the
    // accounting/commerce services; until then both roles grant nothing.
    CLIENT("Client", Set.of()),
    VENDOR("Vendor", Set.of());

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.Set<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    private final String displayName;
    private final Set<String> permissions;
}
