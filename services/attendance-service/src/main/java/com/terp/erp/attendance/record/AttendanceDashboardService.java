package com.terp.erp.attendance.record;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.setting.AttendanceSettingService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Attendance part of the company dashboard (docs/analysis/06 §5.1): "X present today", Attendance Rate and the
 * "Attendance – Last 7 Days" chart, counted from the stored records the same way the records calendar shows them
 * (a day without a record is Not Added and is not counted as absent).
 */
@Service
@RequiredArgsConstructor
public class AttendanceDashboardService {
    private final AttendanceRecordRepository records;
    private final AttendanceSettingService settings;
    private final EmployeeClient employees;
    private final Clock clock;

    public record DayPoint(LocalDate date, long present, long onLeave, long absent) {
    }

    /**
     * @param activeEmployees employees of the company (null when employee-service could not be asked)
     * @param attendanceRateThisMonth % of expected working day-records so far this month; null without data
     */
    public record AttendanceDashboard(LocalDate today, long presentToday, Long activeEmployees,
                                      Double attendanceRateThisMonth, List<DayPoint> last7Days) {
    }

    @Transactional(readOnly = true)
    public AttendanceDashboard dashboard(Long requestedCompanyId) {
        Long companyId = AttendanceRecordService.company(requestedCompanyId);
        LocalDate today = LocalDate.now(clock.withZone(AttendanceRecordService.ZONE));
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate from = monthStart.isBefore(today.minusDays(6)) ? monthStart : today.minusDays(6);
        List<Object[]> rows = records.countByDayAndStatus(companyId, from, today);
        Long headcount;
        List<Holiday> holidays;
        try {
            // TODO(assumption): every employee listed by employee-service counts (no status filter on that call).
            headcount = employees.page(requestedCompanyId, 0, 1).totalElements();
            holidays = employees.holidays(requestedCompanyId, monthStart, today);
        } catch (RuntimeException e) {
            headcount = null;
            holidays = List.of();
        }
        return build(today, rows, headcount, settings.offDays(companyId), holidays);
    }

    static AttendanceDashboard build(LocalDate today, List<Object[]> rows, Long headcount, Set<DayOfWeek> off,
                                     List<Holiday> holidays) {
        Map<LocalDate, Map<ERecordStatus, Long>> byDay = new HashMap<>();
        for (Object[] r : rows) {
            byDay.computeIfAbsent((LocalDate) r[0], k -> new HashMap<>()).merge((ERecordStatus) r[1],
                    ((Number) r[2]).longValue(), (a, b) -> a + b);
        }
        List<DayPoint> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<ERecordStatus, Long> m = byDay.getOrDefault(d, Map.of());
            last7.add(new DayPoint(d, present(m), m.getOrDefault(ERecordStatus.ON_LEAVE, 0L),
                    m.getOrDefault(ERecordStatus.ABSENT, 0L)));
        }
        // Rate = attended (present 1, half day 0.5) / headcount × working days so far this month.
        // TODO(assumption): working day = not an off day and not covered by a holiday of all branches.
        double attended = 0;
        int workingDays = 0;
        for (LocalDate d = today.withDayOfMonth(1); !d.isAfter(today); d = d.plusDays(1)) {
            LocalDate day = d;
            boolean holiday = holidays.stream().anyMatch(h -> h.allBranches()
                    && !day.isBefore(h.startDate()) && !day.isAfter(h.endDate()));
            if (off.contains(d.getDayOfWeek()) || holiday) {
                continue;
            }
            workingDays++;
            Map<ERecordStatus, Long> m = byDay.getOrDefault(d, Map.of());
            attended += m.getOrDefault(ERecordStatus.PRESENT, 0L) + 0.5 * m.getOrDefault(ERecordStatus.HALF_DAY, 0L);
        }
        Double rate = headcount == null || headcount == 0 || workingDays == 0 ? null
                : Math.round(attended * 1000.0 / (headcount * workingDays)) / 10.0;
        return new AttendanceDashboard(today, present(byDay.getOrDefault(today, Map.of())), headcount, rate, last7);
    }

    /** Half days are at work too, so "present" in the chart counts both. */
    private static long present(Map<ERecordStatus, Long> m) {
        return m.getOrDefault(ERecordStatus.PRESENT, 0L) + m.getOrDefault(ERecordStatus.HALF_DAY, 0L);
    }
}
