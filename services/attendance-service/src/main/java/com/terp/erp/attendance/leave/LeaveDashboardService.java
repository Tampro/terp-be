package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.leave.LeaveAppDtos.EmployeeRef;
import com.terp.erp.attendance.regularization.ERegularizationStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave part of the company dashboard (docs/analysis/06 §5.1): Pending Leaves card, Today's Leave card and the Leave
 * Overview donut (this month's applications by status).
 */
@Service
@RequiredArgsConstructor
public class LeaveDashboardService {
    /** Same "today" as the attendance records (Vietnam date until companies carry a zone). */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LeaveApplicationRepository applications;
    private final LeaveTypeRepository types;
    private final EmployeeClient employees;
    private final Clock clock;

    public record TodayLeave(String applicationId, EmployeeRef employee, String leaveType, LocalDate startDate,
                             LocalDate endDate) {
    }

    /** Applications whose start date is in the current month. */
    public record MonthOverview(int year, int month, long approved, long pending, long rejected, long total) {
    }

    @Transactional(readOnly = true)
    public LeaveDashboardView dashboard(Long requestedCompanyId) {
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        List<LeaveApplication> onLeave = applications.covering(companyId, ERegularizationStatus.APPROVED, today);
        Map<Long, String> typeNames = onLeave.isEmpty() ? Map.of()
                : types.findByCompanyIdAndIdIn(companyId, onLeave.stream().map(a -> a.getLeaveTypeId())
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(t -> t.getId(), t -> t.getName()));
        Map<String, Employee> people;
        try {
            people = onLeave.isEmpty() ? Map.of() : employees.all(requestedCompanyId).stream()
                    .collect(Collectors.toMap(e -> e.id(), Function.identity(), (x, y) -> x));
        } catch (RuntimeException e) {
            people = Map.of();
        }
        Map<String, Employee> who = people;
        List<TodayLeave> list = onLeave.stream().map(a -> {
            Employee e = who.get(a.getEmployeeId().toString());
            EmployeeRef ref = e == null ? new EmployeeRef(a.getEmployeeId().toString(), null, null, null, null, null)
                    : LeaveBalanceService.ref(e);
            return new TodayLeave(a.getId().toString(), ref, typeNames.get(a.getLeaveTypeId()), a.getStartDate(),
                    a.getEndDate());
        }).toList();
        LocalDate first = today.withDayOfMonth(1);
        MonthOverview month = overview(today, applications.countByStatusStartingBetween(companyId, first,
                first.plusMonths(1).minusDays(1)));
        return new LeaveDashboardView(today,
                applications.countByCompanyIdAndStatus(companyId, ERegularizationStatus.PENDING), list, month);
    }

    /** JSON: today, pending, todayLeaves, month. */
    public record LeaveDashboardView(LocalDate today, long pending, List<TodayLeave> todayLeaves, MonthOverview month) {
    }

    static MonthOverview overview(LocalDate today, List<Object[]> rows) {
        long approved = 0, pending = 0, rejected = 0;
        for (Object[] r : rows) {
            long n = ((Number) r[1]).longValue();
            switch ((ERegularizationStatus) r[0]) {
                case APPROVED -> approved += n;
                case PENDING -> pending += n;
                case REJECTED -> rejected += n;
            }
        }
        return new MonthOverview(today.getYear(), today.getMonthValue(), approved, pending, rejected,
                approved + pending + rejected);
    }
}
