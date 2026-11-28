package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Delivery permissions checked by delivery-service (Back Office view and assignment, the rider's own deliveries); must
 * match the Liquibase seed 20261129120000_delivery_permission_init_data. Module DELIVERY is always on
 * (EPlanModule.ALWAYS_ON). TODO(assumption): no default rider role — an admin grants DELIVERY_RIDE to a role.
 */
public final class DeliveryPermissions {
    public static final String DELIVERY_VIEW = "DELIVERY_VIEW";
    public static final String DELIVERY_MANAGE = "DELIVERY_MANAGE";
    public static final String DELIVERY_RIDE = "DELIVERY_RIDE";
    public static final Set<String> ALL = Set.of(DELIVERY_VIEW, DELIVERY_MANAGE, DELIVERY_RIDE);

    private DeliveryPermissions() {
    }
}
