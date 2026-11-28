package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Mileage permissions checked by order-service (policy, customer statements and adjustments); must match the Liquibase seed
 * 20261129190100_mileage_permission_init_data. Module ORDER is always on (EPlanModule.ALWAYS_ON).
 */
public final class MileagePermissions {
    public static final String MILEAGE_VIEW = "MILEAGE_VIEW";
    public static final String MILEAGE_MANAGE = "MILEAGE_MANAGE";
    public static final Set<String> ALL = Set.of(MILEAGE_VIEW, MILEAGE_MANAGE);

    private MileagePermissions() {
    }
}
