package com.terp.erp.iam.constant;

import java.util.Set;

/** Company-side notification settings; seeded in 20261120090100_email_template_init_data. */
public final class NotificationPermissions {
    public static final String SETTINGS_UPDATE = "NOTIFICATION_SETTINGS_UPDATE";
    public static final Set<String> ALL = Set.of(SETTINGS_UPDATE);

    private NotificationPermissions() {
    }
}
