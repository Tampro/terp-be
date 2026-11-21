package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by asset-service for §8.2; seeded in 20261120500000_asset_permission_init_data. */
public final class AssetPermissions {
    public static final Set<String> ALL = Set.of("ASSET_VIEW", "ASSET_MANAGE");

    private AssetPermissions() {
    }
}
