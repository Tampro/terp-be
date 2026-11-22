package com.terp.erp.iam.saas;

import lombok.Getter;

import java.util.Set;

/**
 * Modules a plan can include — the module list of the ERP plans (docs/erp/analysis/07 D-07, 08 §4.1). Each one lists
 * the permission modules (`permission.module`) it governs: a company whose plan leaves the module out loses those
 * permissions, so every service answers 403 through its usual @PreAuthorize.
 * Modules without a service yet govern nothing; when a service is built, add its permission module here.
 */
@Getter
public enum EPlanModule {
    // TODO(assumption): HRM covers every HR service of today (people, organisation, time, leave, payroll,
    // announcements, events, assets, documents, employment contracts, meetings) — confirm the split with PO.
    HRM("HRM", Set.of("EMPLOYEE", "ORG", "ATTENDANCE", "LEAVE", "LIFECYCLE", "PAYROLL", "ANNOUNCEMENT", "EVENT",
            "ASSET", "DOCUMENT", "CONTRACT", "MEETING")),
    RECRUITMENT("Recruitment", Set.of("RECRUITMENT")),
    PERFORMANCE("Performance", Set.of("PERFORMANCE")),
    TRAINING("Training", Set.of("TRAINING")),
    ZOOM_MEETING("Zoom Meeting", Set.of("ZOOM")),
    ACCOUNTING("Accounting", Set.of("BANK")),
    DOUBLE_ENTRY("Double Entry", Set.of()),
    BUDGET_PLANNER("Budget Planner", Set.of()),
    FINANCIAL_GOAL("Financial Goal", Set.of()),
    POS("POS", Set.of("POS")),
    QUOTATION("Quotation", Set.of("QUOTATION")),
    CRM("CRM", Set.of("CRM")),
    CONTRACT("Contract", Set.of("CRM_CONTRACT")),
    PROJECT("Project", Set.of("PROJECT")),
    TIMESHEET("Timesheet", Set.of()),
    CALENDAR("Calendar", Set.of()),
    FORM_BUILDER("Form Builder", Set.of("FORM")),
    SUPPORT_TICKET("Support Ticket", Set.of()),
    AI_ASSISTANT("AI Assistant", Set.of()),
    SLACK("Slack", Set.of()),
    TELEGRAM("Telegram", Set.of()),
    TWILIO("Twilio", Set.of()),
    WEBHOOK("Webhook", Set.of());

    /**
     * Permission modules every company keeps whatever its plan: accounts, subscription, files, platform helpdesk, and
     * the commercial core (items and warehouses, proposals, invoices, returns, customers, purchase, vendors).
     */
    // TODO(assumption): the commercial core is always on, like the reference product (items, sales) — confirm with PO.
    public static final Set<String> ALWAYS_ON = Set.of("IAM", "SAAS", "MEDIA", "HELPDESK", "NOTIFICATION",
            "INVENTORY", "SALES", "CUSTOMER", "PURCHASE", "VENDOR");

    private final String displayName;
    private final Set<String> permissionModules;

    EPlanModule(String displayName, Set<String> permissionModules) {
        this.displayName = displayName;
        this.permissionModules = permissionModules;
    }

    /** True when a service of this module exists, i.e. switching it off removes permissions. */
    public boolean available() {
        return !permissionModules.isEmpty();
    }

    /** The plan module governing a permission module, or null when it is always on or not governed. */
    public static EPlanModule governing(String permissionModule) {
        for (EPlanModule m : values()) {
            if (m.permissionModules.contains(permissionModule)) {
                return m;
            }
        }
        return null;
    }
}
