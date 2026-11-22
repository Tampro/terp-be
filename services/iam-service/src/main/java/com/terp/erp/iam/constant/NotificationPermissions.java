package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Company-side notification settings (seeded in 20261120090100_email_template_init_data) and the delivery log of
 * notification-service (seeded in 20261123100000_notification_permission_init_data). Module NOTIFICATION is always
 * on (EPlanModule.ALWAYS_ON).
 */
public final class NotificationPermissions {
    public static final String SETTINGS_UPDATE = "NOTIFICATION_SETTINGS_UPDATE";
    public static final String LOG_VIEW = "NOTIFICATION_LOG_VIEW";
    public static final String LOG_MANAGE = "NOTIFICATION_LOG_MANAGE";
    public static final Set<String> ALL = Set.of(SETTINGS_UPDATE, LOG_VIEW, LOG_MANAGE);

    private NotificationPermissions() {
    }
}
