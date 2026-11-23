package com.terp.erp.iam.constant;

import java.util.HashSet;
import java.util.Set;

/**
 * "Own records only" variants of commercial, CRM, project and support permissions; must match the Liquibase seed
 * 20261123200000_own_scope_permission_init_data. Each keeps the permission module of its base code, so the plan
 * module that governs the base governs the variant too. Services read them through erp-common {@code OwnScope}:
 * the base code reaches every row of the company, the {@code _OWN} code only rows the caller owns.
 */
public final class OwnPermissions {
    public static final String SALES_VIEW_OWN = "SALES_VIEW_OWN";
    public static final String SALES_MANAGE_OWN = "SALES_MANAGE_OWN";
    public static final String QUOTATION_VIEW_OWN = "QUOTATION_VIEW_OWN";
    public static final String QUOTATION_MANAGE_OWN = "QUOTATION_MANAGE_OWN";
    public static final String PURCHASE_VIEW_OWN = "PURCHASE_VIEW_OWN";
    public static final String PURCHASE_MANAGE_OWN = "PURCHASE_MANAGE_OWN";
    public static final String CRM_VIEW_OWN = "CRM_VIEW_OWN";
    public static final String CRM_MANAGE_OWN = "CRM_MANAGE_OWN";
    public static final String CRM_CONTRACT_VIEW_OWN = "CRM_CONTRACT_VIEW_OWN";
    public static final String CRM_CONTRACT_MANAGE_OWN = "CRM_CONTRACT_MANAGE_OWN";
    public static final String PROJECT_VIEW_OWN = "PROJECT_VIEW_OWN";
    public static final String PROJECT_MANAGE_OWN = "PROJECT_MANAGE_OWN";
    public static final String PROJECT_TASK_MANAGE_OWN = "PROJECT_TASK_MANAGE_OWN";
    public static final String SUPPORT_TICKET_VIEW_OWN = "SUPPORT_TICKET_VIEW_OWN";
    public static final String SUPPORT_TICKET_MANAGE_OWN = "SUPPORT_TICKET_MANAGE_OWN";

    public static final Set<String> SALES = Set.of(SALES_VIEW_OWN, SALES_MANAGE_OWN, QUOTATION_VIEW_OWN,
            QUOTATION_MANAGE_OWN);
    public static final Set<String> PURCHASE = Set.of(PURCHASE_VIEW_OWN, PURCHASE_MANAGE_OWN);
    public static final Set<String> CRM = Set.of(CRM_VIEW_OWN, CRM_MANAGE_OWN, CRM_CONTRACT_VIEW_OWN,
            CRM_CONTRACT_MANAGE_OWN);
    public static final Set<String> PROJECT = Set.of(PROJECT_VIEW_OWN, PROJECT_MANAGE_OWN, PROJECT_TASK_MANAGE_OWN);
    public static final Set<String> SUPPORT = Set.of(SUPPORT_TICKET_VIEW_OWN, SUPPORT_TICKET_MANAGE_OWN);

    public static final Set<String> ALL;

    static {
        Set<String> all = new HashSet<>(SALES);
        all.addAll(PURCHASE);
        all.addAll(CRM);
        all.addAll(PROJECT);
        all.addAll(SUPPORT);
        ALL = Set.copyOf(all);
    }

    private OwnPermissions() {
    }
}
