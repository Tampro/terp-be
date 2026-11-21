package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by meeting-service for §9.3 Zoom; seeded in 20261121100000_zoom_permission_init_data. ZOOM_MANAGE
 * also edits the Zoom settings and sees start URLs and passcodes (PO, 2026-11-21).
 */
public final class ZoomPermissions {
    public static final Set<String> ALL = Set.of("ZOOM_VIEW", "ZOOM_MANAGE");

    private ZoomPermissions() {
    }
}
