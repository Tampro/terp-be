package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Client and vendor portal (/api/v1/portal/**); must match the seed 20261123200000_own_scope_permission_init_data.
 * Module PORTAL is always on: each portal page only shows rows of services the company already has.
 */
public final class PortalPermissions {
    public static final String CLIENT = "PORTAL_CLIENT";
    public static final String VENDOR = "PORTAL_VENDOR";

    public static final Set<String> ALL = Set.of(CLIENT, VENDOR);

    private PortalPermissions() {
    }
}
