package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Commercial permissions checked by inventory-, commerce- and accounting-service; must match the Liquibase seed
 * (ERP manual 6, 7, 8, 9, 12.1); purchase and vendors are ERP manual 8.
 */
public final class SalesPermissions {
    /** inventory-service: items, categories, taxes, units, warehouses, stock. */
    public static final Set<String> INVENTORY = Set.of("INVENTORY_VIEW", "INVENTORY_MANAGE");
    /** accounting-service: customers. */
    public static final Set<String> CUSTOMER = Set.of("CUSTOMER_VIEW", "CUSTOMER_MANAGE");
    /** commerce-service: proposals, invoices, returns; SALES_POST posts invoices and approves / completes returns. */
    public static final Set<String> SALES = Set.of("SALES_VIEW", "SALES_MANAGE", "SALES_POST");
    /** commerce-service: quotations (a plan module of its own). */
    public static final Set<String> QUOTATION = Set.of("QUOTATION_VIEW", "QUOTATION_MANAGE");

    /** crm-service: pipelines, stages, labels, sources, leads, deals and their activity, CRM reports. */
    public static final Set<String> CRM = Set.of("CRM_VIEW", "CRM_MANAGE");
    /** crm-service: customer contracts (not the HR employment contracts of document-service, module CONTRACT). */
    public static final Set<String> CRM_CONTRACT = Set.of("CRM_CONTRACT_VIEW", "CRM_CONTRACT_MANAGE");

    /** commerce-service: purchase invoices and returns; PURCHASE_POST posts invoices, approves / completes returns. */
    public static final Set<String> PURCHASE = Set.of("PURCHASE_VIEW", "PURCHASE_MANAGE", "PURCHASE_POST");
    /** accounting-service: vendors. */
    public static final Set<String> VENDOR = Set.of("VENDOR_VIEW", "VENDOR_MANAGE");

    /** commerce-service POS: POS_SELL also lets a cashier read items, stock, warehouses, customers and bank accounts. */
    public static final Set<String> POS = Set.of("POS_VIEW", "POS_SELL", "POS_REPORT");

    /** accounting-service: bank accounts and their transactions. */
    public static final Set<String> BANK = Set.of("BANK_VIEW", "BANK_MANAGE");

    public static final Set<String> ALL;

    static {
        java.util.Set<String> all = new java.util.HashSet<>(INVENTORY);
        all.addAll(CUSTOMER);
        all.addAll(SALES);
        all.addAll(QUOTATION);
        all.addAll(CRM);
        all.addAll(CRM_CONTRACT);
        all.addAll(PURCHASE);
        all.addAll(VENDOR);
        all.addAll(POS);
        all.addAll(BANK);
        ALL = Set.copyOf(all);
    }

    private SalesPermissions() {
    }
}
