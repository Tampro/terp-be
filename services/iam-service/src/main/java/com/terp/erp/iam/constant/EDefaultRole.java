package com.terp.erp.iam.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Roles every new company starts with (docs/analysis/04 §3.3: WorkDo's company/hr/manager/employee plus TERP's
 * executive and accountant). Permission sets cover only the IAM module for now; each business module adds its
 * codes here when it is built.
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
    ACCOUNTANT("Accountant", Set.of());

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.Set<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    private final String displayName;
    private final Set<String> permissions;
}
