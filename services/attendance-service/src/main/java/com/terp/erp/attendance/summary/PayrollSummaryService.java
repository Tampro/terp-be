package com.terp.erp.attendance.summary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.leave.LeaveApplication;
import com.terp.erp.attendance.leave.LeaveApplicationRepository;
import com.terp.erp.attendance.leave.LeaveTypeRepository;
import com.terp.erp.attendance.record.AttendanceRecord;
import com.terp.erp.attendance.record.AttendanceRecordRepository;
import com.terp.erp.attendance.record.ERecordStatus;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance of a pay period per employee, for payroll processing (docs/analysis/06 §8.1.2, PO 2026-11-20).
 * <ul>
 *   <li>Working days: days of the period that are neither a weekly day off nor a holiday of the employee's branch;</li>
 *   <li>Present: PRESENT records count 1, HALF_DAY 0.5;</li>
 *   <li>Leave: approved applications, counted on working days; paid or unpaid by the leave type;</li>
 *   <li>LOP: ABSENT records count 1, HALF_DAY 0.5, on working days not covered by leave;</li>
 *   <li>Overtime: hours and amount stored on the records (the attendance policy's rate per hour).</li>
 * </ul>
 * TODO(assumption): a working day without any record is not LOP — nothing is marked absent automatically
 * (PO 2026-11-19); confirm with BA whether payroll should treat a missing record as absent.
 */
@Service
@RequiredArgsConstructor
public class PayrollSummaryService {
    public static final int MAX_DAYS = 62;
    public static final int MAX_EMPLOYEES = 100;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final AttendanceRecordRepository records;
    private final LeaveApplicationRepository applications;
    private final LeaveTypeRepository leaveTypes;
    private final AttendanceSettingService settings;
    private final EmployeeClient employees;

    public record Day(LocalDate date, LocalTime clockIn, LocalTime clockOut, BigDecimal totalHours,
                      BigDecimal overtimeHours, ERecordStatus status) {
    }

    public record Summary(String employeeId, int workingDays, BigDecimal presentDays, BigDecimal paidLeaveDays,
                          BigDecimal unpaidLeaveDays, BigDecimal lopDays, BigDecimal overtimeHours,
                          BigDecimal overtimeAmount, List<Day> records) {
    }

    @Transactional(readOnly = true)
    public List<Summary> summary(Long requestedCompanyId, LocalDate from, LocalDate to, Collection<Long> ids) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAttendanceError.ATT_004);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EAttendanceError.ATT_051, MAX_DAYS);
        }
        Set<Long> wanted = new LinkedHashSet<>(ids);
        if (wanted.size() > MAX_EMPLOYEES) {
            throw ApiException.badRequest(EAttendanceError.ATT_052, MAX_EMPLOYEES);
        }
        if (wanted.isEmpty()) {
            return List.of();
        }
        // The branch decides which holidays apply; an id outside the company is simply not in this map.
        Map<Long, Employee> byId = employees.all(requestedCompanyId).stream()
                .filter(e -> wanted.contains(Long.valueOf(e.id())))
                .collect(Collectors.toMap(e -> Long.valueOf(e.id()), Function.identity()));
        Set<DayOfWeek> off = settings.offDays(companyId);
        List<Holiday> holidays = employees.holidays(requestedCompanyId, from, to);
        Map<Long, List<AttendanceRecord>> recordsOf = records
                .findByCompanyIdAndEmployeeIdInAndWorkDateBetween(companyId, byId.keySet(), from, to).stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getEmployeeId));
        List<LeaveApplication> leave = applications.approvedOverlapping(companyId, byId.keySet(), from, to);
        Map<Long, Boolean> paid = new HashMap<>();
        leaveTypes.findAllById(leave.stream().map(LeaveApplication::getLeaveTypeId).collect(Collectors.toSet()))
                .forEach(t -> paid.put(t.getId(), t.isPaid()));
        Map<Long, List<LeaveApplication>> leaveOf = leave.stream()
                .collect(Collectors.groupingBy(LeaveApplication::getEmployeeId));
        List<Summary> out = new ArrayList<>();
        for (Long id : wanted) {
            Employee e = byId.get(id);
            if (e != null) {
                out.add(of(id, from, to, off, holidays, e.branchIdOrNull(), recordsOf.getOrDefault(id, List.of()),
                        leaveOf.getOrDefault(id, List.of()), paid));
            }
        }
        return out;
    }

    static Summary of(Long employeeId, LocalDate from, LocalDate to, Set<DayOfWeek> off, List<Holiday> holidays,
                      Long branchId, List<AttendanceRecord> recs, List<LeaveApplication> leave,
                      Map<Long, Boolean> paidByType) {
        Map<LocalDate, AttendanceRecord> byDate = recs.stream()
                .collect(Collectors.toMap(AttendanceRecord::getWorkDate, Function.identity(), (a, b) -> a));
        int working = 0;
        BigDecimal present = BigDecimal.ZERO;
        BigDecimal paidLeave = BigDecimal.ZERO;
        BigDecimal unpaidLeave = BigDecimal.ZERO;
        BigDecimal lop = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate date = d;
            boolean holiday = holidays.stream().anyMatch(h -> !date.isBefore(h.startDate())
                    && !date.isAfter(h.endDate()) && h.appliesTo(branchId));
            boolean workingDay = !off.contains(date.getDayOfWeek()) && !holiday;
            AttendanceRecord r = byDate.get(date);
            if (r != null && r.getStatus() == ERecordStatus.PRESENT) {
                present = present.add(BigDecimal.ONE);
            } else if (r != null && r.getStatus() == ERecordStatus.HALF_DAY) {
                present = present.add(HALF);
            }
            if (!workingDay) {
                continue;
            }
            working++;
            LeaveApplication a = leave.stream()
                    .filter(l -> !date.isBefore(l.getStartDate()) && !date.isAfter(l.getEndDate()))
                    .findFirst().orElse(null);
            if (a != null) {
                // An unknown (deleted) type counts as paid: never deduct on a guess.
                if (paidByType.getOrDefault(a.getLeaveTypeId(), true)) {
                    paidLeave = paidLeave.add(BigDecimal.ONE);
                } else {
                    unpaidLeave = unpaidLeave.add(BigDecimal.ONE);
                }
            } else if (r != null && r.getStatus() == ERecordStatus.ABSENT) {
                lop = lop.add(BigDecimal.ONE);
            } else if (r != null && r.getStatus() == ERecordStatus.HALF_DAY) {
                lop = lop.add(HALF);
            }
        }
        BigDecimal otHours = recs.stream().map(AttendanceRecord::getOvertimeHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otAmount = recs.stream().map(AttendanceRecord::getOvertimeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Day> days = recs.stream().sorted(Comparator.comparing(AttendanceRecord::getWorkDate))
                .map(r -> new Day(r.getWorkDate(), r.getClockIn(), r.getClockOut(), r.getTotalHours(),
                        r.getOvertimeHours(), r.getStatus())).toList();
        return new Summary(employeeId.toString(), working, present, paidLeave, unpaidLeave, lop, otHours, otAmount,
                days);
    }
}
