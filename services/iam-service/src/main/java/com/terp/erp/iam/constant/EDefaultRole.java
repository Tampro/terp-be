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
    // TODO(assumption): COMPANY_ADMIN, HR and MANAGER use the AI assistant (AiPermissions) — confirm with PO.
    COMPANY_ADMIN("Company administrator", union(AiPermissions.ALL,
            union(union(union(union(union(union(union(IamPermissions.ALL, SupportPermissions.ALL), SalesPermissions.ALL),
                                    union(OwnPermissions.ALL, PortalPermissions.ALL)),
                            com.terp.erp.iam.helpdesk.HelpdeskPermissions.ALL),
                    com.terp.erp.iam.saas.SaasPermissions.ALL), MediaPermissions.ALL),
                    union(union(NotificationPermissions.ALL, EmployeePermissions.ALL),
                            union(union(AttendancePermissions.ALL, RecruitmentPermissions.ALL),
                                    union(LifecyclePermissions.ALL, union(PerformancePermissions.ALL,
                                            union(TrainingPermissions.ALL,
                                                    union(PayrollPermissions.ALL,
                                                            union(AssetPermissions.ALL,
                                                                    union(MeetingPermissions.ALL,
                                                                            union(EventPermissions.ALL,
                                                                                    union(ZoomPermissions.ALL, DocumentPermissions.ALL))))))))))))),
    // TODO(assumption): HR manages accounts but not roles, and sees banking data — confirm with BA.
    HR("HR", union(Set.of(IamPermissions.USER_VIEW, IamPermissions.USER_CREATE, IamPermissions.USER_UPDATE,
            IamPermissions.ROLE_VIEW, IamPermissions.PERMISSION_VIEW, SupportPermissions.MESSENGER_USE, AiPermissions.USE), union(union(MediaPermissions.ALL, EmployeePermissions.ALL),
            union(union(AttendancePermissions.ALL, RecruitmentPermissions.ALL),
                    union(LifecyclePermissions.ALL, union(PerformancePermissions.ALL,
                            union(TrainingPermissions.ALL,
                                    union(PayrollPermissions.ALL,
                                            union(AssetPermissions.ALL,
                                                    union(MeetingPermissions.ALL,
                                                            union(EventPermissions.ALL,
                                                                    union(ZoomPermissions.ALL, DocumentPermissions.ALL)))))))))))),
    // TODO(assumption): a manager works their own sales, quotations, purchases, leads/deals, contracts, projects,
    // tasks and tickets (OwnScope: rows they created or are assigned to); full codes stay with COMPANY_ADMIN — confirm.
    MANAGER("Manager", union(AiPermissions.ALL, union(union(OwnPermissions.SALES, OwnPermissions.PURCHASE),
            union(union(OwnPermissions.CRM, OwnPermissions.PROJECT), OwnPermissions.SUPPORT)))),
    // TODO(assumption): every employee clocks in/out, reads their own profile (ERP manual 15) and chats with
    // colleagues (ERP manual 18, Messenger) — confirm with BA. Also (assumption) sees projects they are a member of,
    // works their own tasks and raises / follows their own support tickets.
    EMPLOYEE("Employee", Set.of("ATTENDANCE_SELF", "EMPLOYEE_SELF", SupportPermissions.MESSENGER_USE,
            OwnPermissions.PROJECT_VIEW_OWN, OwnPermissions.PROJECT_TASK_MANAGE_OWN,
            OwnPermissions.SUPPORT_TICKET_VIEW_OWN, OwnPermissions.SUPPORT_TICKET_MANAGE_OWN)),
    // TODO(assumption): the executive board reads its own sales, CRM, contracts and projects (view only) — confirm
    // with BA whether it should rather read everything (full *_VIEW codes).
    EXECUTIVE("Executive board", Set.of(OwnPermissions.SALES_VIEW_OWN, OwnPermissions.QUOTATION_VIEW_OWN,
            OwnPermissions.CRM_VIEW_OWN, OwnPermissions.CRM_CONTRACT_VIEW_OWN, OwnPermissions.PROJECT_VIEW_OWN)),
    ACCOUNTANT("Accountant", Set.of()),
    // TODO(assumption): a client sees only its own invoices, payments, credit notes, contracts, projects and tickets,
    // and a vendor its own purchase invoices, payments and debit notes (docs/erp/analysis/07 §1), all through the
    // /api/v1/portal/** endpoints (PORTAL_*) resolved to the customer / vendor linked to the user. A client raises
    // and follows its own tickets; messenger stays off for clients — confirm with BA.
    CLIENT("Client", Set.of(PortalPermissions.CLIENT, OwnPermissions.SUPPORT_TICKET_VIEW_OWN,
            OwnPermissions.SUPPORT_TICKET_MANAGE_OWN)),
    VENDOR("Vendor", Set.of(PortalPermissions.VENDOR));

    private static Set<String> union(Set<String> a, Set<String> b) {
        java.util.Set<String> all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    private final String displayName;
    private final Set<String> permissions;
}
