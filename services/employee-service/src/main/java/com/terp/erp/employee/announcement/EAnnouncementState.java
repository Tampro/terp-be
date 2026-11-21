package com.terp.erp.employee.announcement;

import java.time.LocalDate;

/** Shown next to the date range; derived from the dates, never stored. */
public enum EAnnouncementState {
    UPCOMING, ACTIVE, EXPIRED;

    static EAnnouncementState of(LocalDate start, LocalDate end, LocalDate today) {
        if (today.isBefore(start)) {
            return UPCOMING;
        }
        return today.isAfter(end) ? EXPIRED : ACTIVE;
    }
}
