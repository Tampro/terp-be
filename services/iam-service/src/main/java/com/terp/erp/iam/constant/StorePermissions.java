package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Store permissions checked by store-service (stores, their warehouse, order hours, fees); must match the Liquibase seed
 * 20261127200100_store_permission_init_data. Module STORE is always on (EPlanModule.ALWAYS_ON).
 */
public final class StorePermissions {
    public static final String STORE_VIEW = "STORE_VIEW";
    public static final String STORE_MANAGE = "STORE_MANAGE";
    public static final Set<String> ALL = Set.of(STORE_VIEW, STORE_MANAGE);

    private StorePermissions() {
    }
}
