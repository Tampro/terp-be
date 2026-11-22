package com.terp.erp.employee.announcement;

/**
 * Stored publication status (FR-ERP-ANN-001: Draft / Active / Inactive). Only ACTIVE announcements reach employees,
 * and only inside their dates (EAnnouncementState). Rows that existed before the status were ACTIVE.
 */
public enum EAnnouncementStatus {
    DRAFT, ACTIVE, INACTIVE
}
