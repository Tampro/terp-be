package com.terp.erp.attendance.leave;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.leave.LeaveAppDtos.BalanceLine;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeRef;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The caller's own leave for the staff dashboard: balances of this year, pending count, latest applications. */
@Service
@RequiredArgsConstructor
public class MyLeaveService {
    static final int RECENT = 5;

    private final LeaveBalanceRepository balances;
    private final LeaveApplicationRepository applications;
    private final LeaveTypeRepository types;
    private final EmployeeClient employees;
    private final Clock clock;

    public record MyApplication(String id, LeaveTypeRef leaveType, LocalDate startDate, LocalDate endDate,
                                int totalDays, ERegularizationStatus status, Instant createdDate) {
    }

    @Schema(description = "My leave: balances of the year (from the last balance sync), pending applications and the "
            + "latest 5 applications")
    public record MyLeave(int year, List<BalanceLine> balances, long pendingApplications,
                          List<MyApplication> recentApplications) {
    }

    @Transactional(readOnly = true)
    public MyLeave mine() {
        Long companyId = TenantGuard.resolveCompany(null, EAttendanceError.ATT_004);
        Employee e = employees.me();
        if (e == null || e.id() == null || e.companyId() == null
                || !Objects.equals(Long.valueOf(e.companyId()), companyId)) {
            throw ApiException.notFound(EAttendanceError.ATT_060);
        }
        Long me = Long.valueOf(e.id());
        int year = LocalDate.now(clock.withZone(LeaveDashboardService.ZONE)).getYear();
        List<LeaveBalance> mine = balances.findByCompanyIdAndYearAndEmployeeIdIn(companyId, year, List.of(me));
        List<LeaveApplication> recent = applications.findTop5ByCompanyIdAndEmployeeIdOrderByCreatedDateDescIdDesc(
                companyId, me);
        Set<Long> typeIds = new HashSet<>();
        mine.forEach(b -> typeIds.add(b.getLeaveTypeId()));
        recent.forEach(a -> typeIds.add(a.getLeaveTypeId()));
        Map<Long, LeaveType> typeById = typeIds.isEmpty() ? Map.of() : types.findByCompanyIdAndIdIn(companyId, typeIds)
                .stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        List<BalanceLine> lines = mine.stream().filter(b -> typeById.containsKey(b.getLeaveTypeId()))
                .sorted(Comparator.comparing(b -> typeById.get(b.getLeaveTypeId()).getName()))
                .map(b -> LeaveBalanceService.line(b, typeById.get(b.getLeaveTypeId()))).toList();
        List<MyApplication> rows = recent.stream().map(a -> {
            LeaveType t = typeById.get(a.getLeaveTypeId());
            return new MyApplication(a.getId().toString(),
                    t == null ? null : new LeaveTypeRef(t.getId().toString(), t.getName(), t.getColor()),
                    a.getStartDate(), a.getEndDate(), a.getTotalDays(), a.getStatus(), a.getCreatedDate());
        }).toList();
        long pending = applications.countByCompanyIdAndEmployeeIdAndStatus(companyId, me,
                ERegularizationStatus.PENDING);
        return new MyLeave(year, lines, pending, rows);
    }
}
