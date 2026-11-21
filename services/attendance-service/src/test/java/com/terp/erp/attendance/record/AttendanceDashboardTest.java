package com.terp.erp.attendance.record;

import org.junit.jupiter.api.Test;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.leave.LeaveDashboardAccess;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceDashboardTest {
    // Wednesday 2026-11-04: month so far = Sun 1 (off), Mon 2, Tue 3 (holiday), Wed 4 → 2 working days.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    void countsTodayLast7DaysAndTheRate() {
        List<Object[]> rows = List.of(
                new Object[]{TODAY, ERecordStatus.PRESENT, 3L},
                new Object[]{TODAY, ERecordStatus.HALF_DAY, 1L},
                new Object[]{TODAY, ERecordStatus.ON_LEAVE, 1L},
                new Object[]{TODAY.minusDays(2), ERecordStatus.PRESENT, 4L},
                new Object[]{TODAY.minusDays(2), ERecordStatus.ABSENT, 1L},
                new Object[]{TODAY.minusDays(6), ERecordStatus.PRESENT, 2L});
        var holiday = new Holiday("1", "Holiday", TODAY.minusDays(1), TODAY.minusDays(1), true, List.of());
        var d = AttendanceDashboardService.build(TODAY, rows, 5L, Set.of(DayOfWeek.SUNDAY), List.of(holiday));

        assertThat(d.presentToday()).isEqualTo(4);
        assertThat(d.last7Days()).hasSize(7);
        assertThat(d.last7Days().get(0).date()).isEqualTo(TODAY.minusDays(6));
        assertThat(d.last7Days().get(6)).isEqualTo(new AttendanceDashboardService.DayPoint(TODAY, 4, 1, 0));
        assertThat(d.last7Days().get(4)).isEqualTo(new AttendanceDashboardService.DayPoint(TODAY.minusDays(2), 4, 0, 1));
        // attended = Mon 2: 4 + Wed 4: 3 + 0.5 = 7.5 of 5 employees × 2 working days
        assertThat(d.attendanceRateThisMonth()).isEqualTo(75.0);
    }

    @Test
    void noHeadcountOrNoWorkingDayGivesNoRate() {
        assertThat(AttendanceDashboardService.build(TODAY, List.of(), null, Set.of(), List.of())
                .attendanceRateThisMonth()).isNull();
        assertThat(AttendanceDashboardService.build(TODAY, List.of(), 0L, Set.of(), List.of())
                .attendanceRateThisMonth()).isNull();
    }

    @Test
    void leaveOverviewCountsByStatus() {
        var m = LeaveDashboardAccess.overview(TODAY, List.of(
                new Object[]{ERegularizationStatus.APPROVED, 3L},
                new Object[]{ERegularizationStatus.PENDING, 2L},
                new Object[]{ERegularizationStatus.REJECTED, 1L}));
        assertThat(m.approved()).isEqualTo(3);
        assertThat(m.pending()).isEqualTo(2);
        assertThat(m.rejected()).isEqualTo(1);
        assertThat(m.total()).isEqualTo(6);
        assertThat(m.month()).isEqualTo(11);
    }
}
