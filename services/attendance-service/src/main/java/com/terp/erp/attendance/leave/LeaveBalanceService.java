package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.EmployeePage;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.leave.LeaveAppDtos.BalanceCard;
import com.terp.erp.attendance.leave.LeaveAppDtos.BalanceLine;
import com.terp.erp.attendance.leave.LeaveAppDtos.BalancePage;
import com.terp.erp.attendance.leave.LeaveAppDtos.EmployeeRef;
import com.terp.erp.attendance.leave.LeaveAppDtos.SyncDto;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeRef;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave Balances page (docs/analysis/06 §6.5.2). Sync writes one balance per (employee, active leave type) of a year:
 * allocated = the type's days per year, used = approved leave days of that year, carried forward = what was left last
 * year up to the policy's carry-forward limit; a manual adjustment already on the row is kept.
 * TODO(assumption): terminated employees get no balance; the allocation is the full year whatever the joining date
 * (no pro-rata) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {
    private final LeaveBalanceRepository balances;
    private final LeaveBalanceSyncRepository syncs;
    private final LeaveTypeRepository types;
    private final LeavePolicyRepository policies;
    private final LeaveApplicationRepository applications;
    private final EmployeeClient employees;
    private final Clock clock;

    @Transactional
    public SyncDto sync(Long requestedCompanyId, int year) {
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        List<Employee> people = employees.all(requestedCompanyId).stream()
                .filter(e -> !"TERMINATED".equals(e.status())).toList();
        List<LeaveType> active = types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(companyId, EStatus.ACTIVE);
        Map<Long, LeavePolicy> policyByType = new HashMap<>();
        for (LeaveType t : active) {
            policies.findFirstByLeaveTypeIdAndStatusAndDeletedFalse(t.getId(), EStatus.ACTIVE)
                    .ifPresent(p -> policyByType.put(t.getId(), p));
        }
        Map<String, Long> used = new HashMap<>();
        for (Object[] r : applications.approvedDays(companyId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            used.put(r[0] + ":" + r[1], ((Number) r[2]).longValue());
        }
        Map<String, LeaveBalance> previous = balances.findByCompanyIdAndYear(companyId, year - 1).stream()
                .collect(Collectors.toMap(b -> b.getEmployeeId() + ":" + b.getLeaveTypeId(), Function.identity()));
        Map<String, LeaveBalance> current = balances.findByCompanyIdAndYear(companyId, year).stream()
                .collect(Collectors.toMap(b -> b.getEmployeeId() + ":" + b.getLeaveTypeId(), Function.identity()));
        int written = 0;
        for (Employee e : people) {
            Long employeeId = Long.valueOf(e.id());
            for (LeaveType t : active) {
                String key = employeeId + ":" + t.getId();
                LeaveBalance b = current.get(key);
                if (b == null) {
                    b = new LeaveBalance();
                    b.setCompanyId(companyId);
                    b.setEmployeeId(employeeId);
                    b.setLeaveTypeId(t.getId());
                    b.setYear(year);
                }
                LeavePolicy p = policyByType.get(t.getId());
                b.setLeavePolicyId(p == null ? null : p.getId());
                b.setAllocatedDays(BigDecimal.valueOf(t.getMaxDaysPerYear()));
                b.setUsedDays(BigDecimal.valueOf(used.getOrDefault(key, 0L)));
                b.setCarriedForward(carried(previous.get(key), p));
                balances.save(b);
                written++;
            }
        }
        LeaveBalanceSync s = new LeaveBalanceSync();
        s.setCompanyId(companyId);
        s.setYear(year);
        s.setSyncedAt(clock.instant());
        s.setSyncedBy(CurrentUser.find().map(ErpPrincipal::username).orElse(null));
        s.setEmployees(people.size());
        s.setBalances(written);
        return dto(syncs.save(s));
    }

    /** What was left last year, up to the carry-forward limit; nothing without a policy or a balance last year. */
    static BigDecimal carried(LeaveBalance previous, LeavePolicy policy) {
        if (previous == null || policy == null || policy.getCarryForwardLimit() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal left = previous.available().max(BigDecimal.ZERO);
        return left.min(BigDecimal.valueOf(policy.getCarryForwardLimit()));
    }

    /** Cards of the year: one per employee (paged like the employee list), one line per leave type. */
    @Transactional(readOnly = true)
    public BalancePage cards(Long requestedCompanyId, int year, Long employeeId, String keyword, int page, int size) {
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        List<Employee> people;
        long total;
        if (employeeId != null) {
            people = List.of(employees.get(employeeId));
            total = 1;
        } else {
            EmployeePage p = employees.page(requestedCompanyId, keyword, page, size);
            people = p.content();
            total = p.totalElements();
        }
        List<Long> ids = people.stream().map(e -> Long.valueOf(e.id())).toList();
        Map<Long, List<LeaveBalance>> byEmployee = ids.isEmpty() ? Map.of()
                : balances.findByCompanyIdAndYearAndEmployeeIdIn(companyId, year, ids).stream()
                .collect(Collectors.groupingBy(LeaveBalance::getEmployeeId));
        Map<Long, LeaveType> typeById = types.findByCompanyIdAndIdIn(companyId, byEmployee.values().stream()
                        .flatMap(List::stream).map(LeaveBalance::getLeaveTypeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(LeaveType::getId, Function.identity()));
        List<BalanceCard> cards = new ArrayList<>();
        for (Employee e : people) {
            List<BalanceLine> lines = byEmployee.getOrDefault(Long.valueOf(e.id()), List.of()).stream()
                    .filter(b -> typeById.containsKey(b.getLeaveTypeId()))
                    .sorted(Comparator.comparing(b -> typeById.get(b.getLeaveTypeId()).getName()))
                    .map(b -> line(b, typeById.get(b.getLeaveTypeId()))).toList();
            cards.add(new BalanceCard(ref(e), lines));
        }
        var last = syncs.findFirstByCompanyIdAndYearOrderBySyncedAtDesc(companyId, year);
        return new BalancePage(year, last.map(LeaveBalanceSync::getSyncedAt).orElse(null), cards, page, size, total);
    }

    @Transactional(readOnly = true)
    public Page<SyncDto> history(Long requestedCompanyId, Integer year, String keyword, Pageable pageable) {
        return syncs.search(LeaveTypeService.company(requestedCompanyId), year, Texts.kw(keyword), pageable)
                .map(LeaveBalanceService::dto);
    }

    static BalanceLine line(LeaveBalance b, LeaveType t) {
        return new BalanceLine(new LeaveTypeRef(t.getId().toString(), t.getName(), t.getColor()), b.getAllocatedDays(),
                b.getCarriedForward(), b.getManualAdjustment(), b.total(), b.getUsedDays(), b.available());
    }

    static EmployeeRef ref(Employee e) {
        return new EmployeeRef(e.id(), e.fullName(), e.email(),
                e.employeeCode() != null ? e.employeeCode() : e.employeeNo(),
                e.designation() == null ? null : e.designation().name(), e.profileImageUrl());
    }

    private static SyncDto dto(LeaveBalanceSync s) {
        return new SyncDto(s.getId().toString(), s.getYear(), s.getSyncedBy(), s.getSyncedAt(), s.getCreatedDate(),
                s.getEmployees(), s.getBalances());
    }
}
