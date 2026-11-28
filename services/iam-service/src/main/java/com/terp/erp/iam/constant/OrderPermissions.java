package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Order permissions checked by order-service (Back Office view of customer orders); must match the Liquibase seed
 * 20261128220000_order_permission_init_data. Module ORDER is always on (EPlanModule.ALWAYS_ON).
 */
public final class OrderPermissions {
    public static final String ORDER_VIEW = "ORDER_VIEW";
    public static final Set<String> ALL = Set.of(ORDER_VIEW);

    private OrderPermissions() {
    }
}
