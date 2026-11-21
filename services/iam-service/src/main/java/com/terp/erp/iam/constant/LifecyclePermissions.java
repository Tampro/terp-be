package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by employee-service for §7.2; seeded in 20261120340000_lifecycle_permission_init_data. */
public final class LifecyclePermissions {
    public static final Set<String> ALL = Set.of("LIFECYCLE_VIEW", "LIFECYCLE_MANAGE");

    private LifecyclePermissions() {
    }
}
