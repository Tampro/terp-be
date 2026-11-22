package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by attendance-service; seeded in 20261120200000_attendance_permission_init_data and
 * 20261120250000_leave_permission_init_data.
 */
public final class AttendancePermissions {
    public static final Set<String> ALL = Set.of("ATTENDANCE_VIEW", "ATTENDANCE_MANAGE", "LEAVE_VIEW", "LEAVE_MANAGE",
            "ATTENDANCE_SELF");

    private AttendancePermissions() {
    }
}
