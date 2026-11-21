package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by meeting-service for §9.2 Events; seeded in 20261121090000_event_permission_init_data. EVENT_APPROVE
 * approves / rejects pending events (PO, 2026-11-21).
 */
public final class EventPermissions {
    public static final Set<String> ALL = Set.of("EVENT_VIEW", "EVENT_MANAGE", "EVENT_APPROVE");

    private EventPermissions() {
    }
}
