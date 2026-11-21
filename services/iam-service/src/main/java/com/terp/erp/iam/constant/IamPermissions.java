package com.terp.erp.iam.constant;

import java.util.Set;

/** Permission codes of the IAM module; must match the Liquibase seed. Company management is super-admin only. */
public final class IamPermissions {
    public static final String ROLE_VIEW = "IAM_ROLE_VIEW";
    public static final String ROLE_CREATE = "IAM_ROLE_CREATE";
    public static final String ROLE_UPDATE = "IAM_ROLE_UPDATE";
    public static final String ROLE_DELETE = "IAM_ROLE_DELETE";
    public static final String USER_VIEW = "IAM_USER_VIEW";
    public static final String USER_CREATE = "IAM_USER_CREATE";
    public static final String USER_UPDATE = "IAM_USER_UPDATE";
    public static final String PERMISSION_VIEW = "IAM_PERMISSION_VIEW";
    /** Company settings of the IAM: IP restriction (PO, 2026-11-21). */
    public static final String SETTINGS_MANAGE = "IAM_SETTINGS_MANAGE";

    public static final Set<String> ALL = Set.of(ROLE_VIEW, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE,
            USER_VIEW, USER_CREATE, USER_UPDATE, PERMISSION_VIEW, SETTINGS_MANAGE);

    private IamPermissions() {
    }
}
