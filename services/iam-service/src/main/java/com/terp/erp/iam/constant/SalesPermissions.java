package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Commercial permissions checked by inventory-, commerce- and accounting-service; must match the Liquibase seed
 * (ERP manual 6, 8, 9, 12.1).
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

    public static final Set<String> ALL;

    static {
        java.util.Set<String> all = new java.util.HashSet<>(INVENTORY);
        all.addAll(CUSTOMER);
        all.addAll(SALES);
        all.addAll(QUOTATION);
        ALL = Set.copyOf(all);
    }

    private SalesPermissions() {
    }
}
