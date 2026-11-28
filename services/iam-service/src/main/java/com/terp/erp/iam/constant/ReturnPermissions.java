package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Return permissions checked by order-service (customer returns of delivered items); must match the Liquibase seed
 * 20261130100100_order_return_permission_init_data. Module ORDER is always on (EPlanModule.ALWAYS_ON).
 */
public final class ReturnPermissions {
    public static final String ORDER_RETURN_VIEW = "ORDER_RETURN_VIEW";
    public static final String ORDER_RETURN_MANAGE = "ORDER_RETURN_MANAGE";
    public static final Set<String> ALL = Set.of(ORDER_RETURN_VIEW, ORDER_RETURN_MANAGE);

    private ReturnPermissions() {
    }
}
