package com.terp.erp.attendance.leave;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.Holiday;
import com.terp.erp.attendance.client.MediaClient;
import com.terp.erp.attendance.client.MediaClient.MediaFile;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationDto;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationForm;
import com.terp.erp.attendance.leave.LeaveAppDtos.Calendar;
import com.terp.erp.attendance.leave.LeaveAppDtos.CalendarLeave;
import com.terp.erp.attendance.leave.LeaveAppDtos.CalendarRow;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeRef;
import com.terp.erp.attendance.record.AttendanceRecordService;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.setting.AttendanceSettingService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Leave Applications page (docs/analysis/06 §6.5.1; PO, 2026-11-19): days = working days of the range (the company's
 * days off and the holidays of the employee's branch excluded); approving uses the balance and marks every working day
 * ON_LEAVE in the attendance calendar. The active policy of the leave type decides the days per application and
 * whether an approval is needed (none = approved at once). Pending days are reserved on the balance.
 * TODO(assumption): a leave stays within one calendar year (ATT_042); only PENDING applications can be edited or
 * deleted (ATT_020; ATT_048 is no longer raised) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class LeaveApplicationService {
    static final int MAX_CALENDAR_DAYS = 42;
    private static final List<Long> NONE = List.of(-1L);

    private final LeaveApplicationRepository applications;
    private final LeaveTypeService typeService;
    private final LeaveTypeRepository types;
    private final LeavePolicyRepository policies;
    private final LeaveBalanceRepository balances;
    private final AttendanceSettingService settings;
    private final AttendanceRecordService records;
    private final EmployeeClient employees;
    private final MediaClient media;
    private final Clock clock;

    // ------------------------------------------------------------------ reads

    /** Pending / Rejected tabs (and approved ones); keyword = the employee's name, email, number or code. */
    @Transactional(readOnly = true)
    public Page<ApplicationDto> search(Long requestedCompanyId, ERegularizationStatus status, Long employeeId,
                                       Long leaveTypeId, Integer year, String keyword, Pageable pageable) {
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        Map<String, Employee> people = people(requestedCompanyId);
        boolean any = keyword == null || keyword.isBlank();
        List<Long> ids = any ? NONE : matching(people, keyword);
        LocalDate from = year == null ? null : LocalDate.of(year, 1, 1);
        LocalDate to = year == null ? null : LocalDate.of(year, 12, 31);
        Page<LeaveApplication> page = applications.search(companyId, status, employeeId, leaveTypeId, from, to, any,
                ids, pageable);
        Map<Long, LeaveType> typeById = typesOf(companyId, page.getContent());
        return page.map(a -> dto(a, people.get(a.getEmployeeId().toString()), typeById.get(a.getLeaveTypeId()), false));
    }

    /** Leaves (pending and approved) overlapping [from, to], one row per employee who has one. */
    @Transactional(readOnly = true)
    public Calendar calendar(Long requestedCompanyId, LocalDate from, LocalDate to, Long employeeId, String keyword) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
        Long companyId = LeaveTypeService.company(requestedCompanyId);
        Map<String, Employee> people = people(requestedCompanyId);
        boolean any = keyword == null || keyword.isBlank();
        List<LeaveApplication> list = applications.search(companyId, null, employeeId, null, from, to, any,
                        any ? NONE : matching(people, keyword), PageRequest.of(0, 2000, Sort.by("startDate")))
                .getContent().stream().filter(a -> a.getStatus() != ERegularizationStatus.REJECTED).toList();
        Map<Long, LeaveType> typeById = typesOf(companyId, list);
        Map<Long, List<CalendarLeave>> byEmployee = new LinkedHashMap<>();
        for (LeaveApplication a : list) {
            byEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(new CalendarLeave(
                    a.getId().toString(), ref(typeById.get(a.getLeaveTypeId())), a.getStartDate(), a.getEndDate(),
                    a.getTotalDays(), a.getStatus()));
        }
        List<CalendarRow> rows = byEmployee.entrySet().stream().map(en -> {
            Employee e = people.get(en.getKey().toString());
            return new CalendarRow(e == null ? new LeaveAppDtos.EmployeeRef(en.getKey().toString(), null, null, null,
                    null, null) : LeaveBalanceService.ref(e), en.getValue());
        }).sorted(Comparator.comparing(r -> r.employee().fullName() == null ? "" : r.employee().fullName())).toList();
        return new Calendar(from, to, rows);
    }

    @Transactional(readOnly = true)
    public ApplicationDto get(Long id) {
        LeaveApplication a = require(id);
        return dto(a, employeeOrNull(a.getEmployeeId()), types.findById(a.getLeaveTypeId()).orElse(null), true);
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public ApplicationDto create(ApplicationForm form) {
        Employee e = employees.get(form.employeeId());
        LeaveApplication a = new LeaveApplication();
        LeaveType t = apply(a, e, form);
        applications.save(a);
        LeavePolicy p = a.getLeavePolicyId() == null ? null : policies.findById(a.getLeavePolicyId()).orElse(null);
        if (p != null && !p.isRequiresApproval()) {
            approveNow(a, e, null);
        }
        return dto(a, e, t, true);
    }

    /**
     * Full replacement of a PENDING application (ATT_020 once approved or rejected — manual §20; a rejected leave is
     * applied for again as a new application). TODO(assumption): earlier a REJECTED application could be edited back to
     * PENDING; confirm with BA that resubmitting means a new application.
     */
    @Transactional
    public ApplicationDto update(Long id, ApplicationForm form) {
        LeaveApplication a = requirePending(id);
        Employee e = employees.get(form.employeeId());
        a.setStatus(ERegularizationStatus.PENDING);
        a.setManagerComments(null);
        a.setDecidedAt(null);
        a.setDecidedBy(null);
        LeaveType t = apply(a, e, form);
        return dto(a, e, t, true);
    }

    @Transactional
    public ApplicationDto approve(Long id, String comments) {
        LeaveApplication a = requirePending(id);
        Employee e = employees.get(a.getEmployeeId());
        assertBalance(a, a.getId());
        approveNow(a, e, comments);
        return dto(a, e, types.findById(a.getLeaveTypeId()).orElse(null), true);
    }

    @Transactional
    public ApplicationDto reject(Long id, String comments) {
        LeaveApplication a = requirePending(id);
        decide(a, ERegularizationStatus.REJECTED, comments);
        return dto(a, employeeOrNull(a.getEmployeeId()), types.findById(a.getLeaveTypeId()).orElse(null), true);
    }

    @Transactional
    public void delete(Long id) {
        applications.delete(requirePending(id));
    }

    /** Validates the form for this employee and fills the application (days, policy, attachment). */
    private LeaveType apply(LeaveApplication a, Employee e, ApplicationForm f) {
        Long companyId = companyOf(e);
        LeaveType t = typeService.require(f.leaveTypeId());
        if (!Objects.equals(t.getCompanyId(), companyId)) {
            throw ApiException.notFound(EAttendanceError.ATT_034);
        }
        if (t.getStatus() != EStatus.ACTIVE) {
            throw ApiException.badRequest(EAttendanceError.ATT_040);
        }
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EAttendanceError.ATT_041);
        }
        if (f.endDate().getYear() != f.startDate().getYear()) {
            throw ApiException.badRequest(EAttendanceError.ATT_042);
        }
        List<LocalDate> days = workingDays(companyId, e.branchIdOrNull(), f.startDate(), f.endDate());
        if (days.isEmpty()) {
            throw ApiException.badRequest(EAttendanceError.ATT_043);
        }
        LeavePolicy p = policies.findFirstByLeaveTypeIdAndStatusAndDeletedFalse(t.getId(), EStatus.ACTIVE).orElse(null);
        if (p != null && (days.size() < p.getMinDaysPerApplication() || days.size() > p.getMaxDaysPerApplication())) {
            throw ApiException.badRequest(EAttendanceError.ATT_044, p.getMinDaysPerApplication(),
                    p.getMaxDaysPerApplication());
        }
        Long employeeId = Long.valueOf(e.id());
        if (applications.overlaps(employeeId, f.startDate(), f.endDate(), a.getId() == null ? -1L : a.getId())) {
            throw ApiException.badRequest(EAttendanceError.ATT_045);
        }
        if (f.attachmentFileId() != null) {
            media.require(f.attachmentFileId(), companyId);
        }
        a.setCompanyId(companyId);
        a.setEmployeeId(employeeId);
        a.setLeaveTypeId(t.getId());
        a.setLeavePolicyId(p == null ? null : p.getId());
        a.setStartDate(f.startDate());
        a.setEndDate(f.endDate());
        a.setTotalDays(days.size());
        a.setReason(f.reason().trim());
        a.setAttachmentFileId(f.attachmentFileId());
        assertBalance(a, a.getId() == null ? -1L : a.getId());
        return t;
    }

    /** Balance of the type and year must hold these days plus the employee's other pending ones (ATT_046 / ATT_047). */
    private LeaveBalance assertBalance(LeaveApplication a, Long selfId) {
        int year = a.getStartDate().getYear();
        LeaveBalance b = balances.findByEmployeeIdAndLeaveTypeIdAndYear(a.getEmployeeId(), a.getLeaveTypeId(), year)
                .orElseThrow(() -> ApiException.badRequest(EAttendanceError.ATT_046,
                        types.findById(a.getLeaveTypeId()).map(LeaveType::getName).orElse(""), year));
        long pending = applications.pendingDays(a.getEmployeeId(), a.getLeaveTypeId(), LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31), selfId);
        BigDecimal free = b.available().subtract(BigDecimal.valueOf(pending));
        if (free.compareTo(BigDecimal.valueOf(a.getTotalDays())) < 0) {
            throw ApiException.badRequest(EAttendanceError.ATT_047, free.max(BigDecimal.ZERO).stripTrailingZeros()
                    .toPlainString());
        }
        return b;
    }

    /** Uses the balance and marks every working day ON_LEAVE. */
    private void approveNow(LeaveApplication a, Employee e, String comments) {
        LeaveBalance b = balances.findByEmployeeIdAndLeaveTypeIdAndYear(a.getEmployeeId(), a.getLeaveTypeId(),
                a.getStartDate().getYear()).orElseThrow();
        b.setUsedDays(b.getUsedDays().add(BigDecimal.valueOf(a.getTotalDays())));
        for (LocalDate d : workingDays(a.getCompanyId(), e.branchIdOrNull(), a.getStartDate(), a.getEndDate())) {
            records.markOnLeave(a.getCompanyId(), a.getEmployeeId(), d);
        }
        decide(a, ERegularizationStatus.APPROVED, comments);
    }

    private void decide(LeaveApplication a, ERegularizationStatus status, String comments) {
        a.setStatus(status);
        a.setManagerComments(Texts.blankToNull(comments));
        a.setDecidedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        a.setDecidedAt(clock.instant());
    }

    /** Days of [from, to] that are neither a weekly day off of the company nor a holiday of the employee's branch. */
    List<LocalDate> workingDays(Long companyId, Long branchId, LocalDate from, LocalDate to) {
        Set<DayOfWeek> off = settings.offDays(companyId);
        List<Holiday> holidays = employees.holidays(companyId, from, to);
        return workingDays(off, holidays, branchId, from, to);
    }

    static List<LocalDate> workingDays(Set<DayOfWeek> off, List<Holiday> holidays, Long branchId, LocalDate from,
                                       LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate day = d;
            boolean holiday = holidays.stream().anyMatch(h -> !day.isBefore(h.startDate()) && !day.isAfter(h.endDate())
                    && h.appliesTo(branchId));
            if (!off.contains(d.getDayOfWeek()) && !holiday) {
                out.add(d);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private ApplicationDto dto(LeaveApplication a, Employee e, LeaveType t, boolean detail) {
        MediaFile f = detail && a.getAttachmentFileId() != null
                && CurrentUser.find().map(p -> p.has("MEDIA_VIEW")).orElse(false)
                ? media.find(a.getAttachmentFileId()) : null;
        LeaveAppDtos.EmployeeRef ref = e == null
                ? new LeaveAppDtos.EmployeeRef(a.getEmployeeId().toString(), null, null, null, null, null)
                : LeaveBalanceService.ref(e);
        return new ApplicationDto(a.getId().toString(), ref, ref(t), a.getLeavePolicyId() == null ? null
                : a.getLeavePolicyId().toString(), a.getStartDate(), a.getEndDate(), a.getTotalDays(), a.getReason(),
                a.getAttachmentFileId() == null ? null : a.getAttachmentFileId().toString(),
                f == null ? null : f.name(), f == null ? null : f.downloadUrl(), a.getStatus(), a.getManagerComments(),
                a.getDecidedAt(), a.getCreatedDate(), a.getUpdatedDate());
    }

    private static LeaveTypeRef ref(LeaveType t) {
        return t == null ? null : new LeaveTypeRef(t.getId().toString(), t.getName(), t.getColor());
    }

    private Map<Long, LeaveType> typesOf(Long companyId, List<LeaveApplication> list) {
        return types.findByCompanyIdAndIdIn(companyId, list.stream().map(LeaveApplication::getLeaveTypeId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(LeaveType::getId, Function.identity()));
    }

    private Map<String, Employee> people(Long requestedCompanyId) {
        return employees.all(requestedCompanyId).stream()
                .collect(Collectors.toMap(Employee::id, Function.identity(), (x, y) -> x));
    }

    static List<Long> matching(Map<String, Employee> people, String keyword) {
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        List<Long> ids = people.values().stream().filter(e -> has(e.fullName(), k) || has(e.email(), k)
                        || has(e.employeeNo(), k) || has(e.employeeCode(), k))
                .map(e -> Long.valueOf(e.id())).toList();
        return ids.isEmpty() ? NONE : ids;
    }

    private static boolean has(String s, String k) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(k);
    }

    private Employee employeeOrNull(Long id) {
        try {
            return employees.get(id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static Long companyOf(Employee e) {
        Long companyId = Long.valueOf(e.companyId());
        TenantGuard.assertSameCompany(companyId, EAttendanceError.ATT_039);
        return companyId;
    }

    private LeaveApplication require(Long id) {
        LeaveApplication a = applications.findById(id)
                .orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_039));
        TenantGuard.assertSameCompany(a.getCompanyId(), EAttendanceError.ATT_039);
        return a;
    }

    private LeaveApplication requirePending(Long id) {
        LeaveApplication a = require(id);
        if (a.getStatus() != ERegularizationStatus.PENDING) {
            throw ApiException.badRequest(EAttendanceError.ATT_020);
        }
        return a;
    }
}
