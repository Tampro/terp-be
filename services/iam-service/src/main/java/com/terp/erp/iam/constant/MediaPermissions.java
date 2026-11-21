package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * Codes checked by media-service (MediaController); seeded in 20261119090100_media_permission_init_data.
 * Default roles that get them: COMPANY_ADMIN and HR (PO, 2026-11-19).
 */
public final class MediaPermissions {
    public static final Set<String> ALL = Set.of("MEDIA_VIEW", "MEDIA_UPLOAD", "MEDIA_DELETE");

    private MediaPermissions() {
    }
}
