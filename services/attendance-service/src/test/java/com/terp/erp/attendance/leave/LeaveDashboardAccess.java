package com.terp.erp.attendance.leave;

import java.time.LocalDate;
import java.util.List;

/** Test access to the package-private leave overview count. */
public final class LeaveDashboardAccess {
    private LeaveDashboardAccess() {
    }

    public static LeaveDashboardService.MonthOverview overview(LocalDate today, List<Object[]> rows) {
        return LeaveDashboardService.overview(today, rows);
    }
}
