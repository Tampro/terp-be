package com.terp.erp.iam.constant;

import java.util.Set;

/** Codes checked by meeting-service for §9.1; seeded in 20261120520000_meeting_permission_init_data. */
public final class MeetingPermissions {
    public static final Set<String> ALL = Set.of("MEETING_VIEW", "MEETING_MANAGE");

    private MeetingPermissions() {
    }
}
