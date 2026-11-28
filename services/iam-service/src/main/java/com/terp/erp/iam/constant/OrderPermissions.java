package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Order permissions checked by order-service (Back Office view of customer orders); must match the Liquibase seed
 * 20261128220000_order_permission_init_data and 20261129200100_order_cancel_permission_init_data (ORDER_CANCEL). Module ORDER is always on (EPlanModule.ALWAYS_ON).
 */
public final class OrderPermissions {
    public static final String ORDER_VIEW = "ORDER_VIEW";
    public static final String ORDER_CANCEL = "ORDER_CANCEL";
    public static final Set<String> ALL = Set.of(ORDER_VIEW, ORDER_CANCEL);

    private OrderPermissions() {
    }
}
