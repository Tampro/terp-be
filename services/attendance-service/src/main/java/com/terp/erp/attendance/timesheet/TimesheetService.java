package com.terp.erp.attendance.timesheet;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.attendance.client.EmployeeClient;
import com.terp.erp.attendance.client.EmployeeClient.Employee;
import com.terp.erp.attendance.client.EmployeeClient.EmployeePage;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.common.Texts;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.timesheet.TimesheetDtos.DayCell;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryDto;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryForm;
import com.terp.erp.attendance.timesheet.TimesheetDtos.Month;
import com.terp.erp.attendance.timesheet.TimesheetDtos.MonthRow;
import com.terp.erp.attendance.timesheet.TimesheetDtos.StatusCounts;
import com.terp.erp.attendance.timesheet.TimesheetDtos.Week;
import com.terp.erp.attendance.timesheet.TimesheetDtos.WeekRow;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Timesheet page (docs/analysis/06 §6.4.2): a Monday–Sunday grid per employee, entries approved or rejected one by
 * one. Admin/HR enter them for now (PO, 2026-11-19).
 * TODO(assumption): an approved entry is final (ATT_030); editing a rejected one sends it back to PENDING; a day holds
 * at most 24 h (ATT_031); no hours on future dates (ATT_032) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class TimesheetService {
    static final BigDecimal DAY_MAX = new BigDecimal("24");
    static final BigDecimal MIN_TIME = new BigDecimal("0.25");
    /** TODO(assumption): "today" is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TimeEntryRepository entries;
    private final EmployeeClient employees;
    private final Clock clock;

    /** The week containing {@code anyDay}; employees are paged by employee number, keyword is employee-service's. */
    @Transactional(readOnly = true)
    public Week week(Long requestedCompanyId, LocalDate anyDay, Long employeeId, String keyword,
                     ERegularizationStatus status, int page, int size) {
        Long companyId = company(requestedCompanyId);
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
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
        Map<Long, List<TimeEntry>> byEmployee = ids.isEmpty() ? Map.of()
                : entries.forEmployees(companyId, ids, monday, sunday, status).stream()
                .collect(Collectors.groupingBy(TimeEntry::getEmployeeId));
        List<WeekRow> rows = people.stream()
                .map(e -> row(e, monday, byEmployee.getOrDefault(Long.valueOf(e.id()), List.of()))).toList();
        StatusCounts counts = ids.isEmpty() ? new StatusCounts(0, 0, 0, 0)
                : counts(entries.countByStatus(companyId, ids, monday, sunday));
        return new Week(monday, sunday, rows, counts, page, size, total);
    }

    /** hours + minutes / 60, kept to 2 decimals (the column's scale). */
    static BigDecimal total(EntryForm f) {
        BigDecimal minutes = f.minutes() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(f.minutes()).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        return f.hours().add(minutes).setScale(2, RoundingMode.HALF_UP);
    }

    /** The month grid (ERP manual 11.5): one row per employee, one cell per day, totals per row, day and month. */
    @Transactional(readOnly = true)
    public Month month(Long requestedCompanyId, int year, int month, Long employeeId, String keyword,
                       ERegularizationStatus status, int page, int size) {
        Long companyId = company(requestedCompanyId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
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
        Map<Long, List<TimeEntry>> byEmployee = ids.isEmpty() ? Map.of()
                : entries.forEmployees(companyId, ids, first, last, status).stream()
                .collect(Collectors.groupingBy(TimeEntry::getEmployeeId));
        int days = ym.lengthOfMonth();
        List<MonthRow> rows = new ArrayList<>();
        List<BigDecimal> dayTotals = new ArrayList<>(Collections.nCopies(days, BigDecimal.ZERO));
        BigDecimal grand = BigDecimal.ZERO;
        for (Employee e : people) {
            List<DayCell> cells = cells(first, days, byEmployee.getOrDefault(Long.valueOf(e.id()), List.of()));
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < days; i++) {
                dayTotals.set(i, dayTotals.get(i).add(cells.get(i).hours()));
                sum = sum.add(cells.get(i).hours());
            }
            grand = grand.add(sum);
            rows.add(new MonthRow(e.id(), e.fullName(), e.designation() == null ? null : e.designation().name(),
                    e.profileImageUrl(), cells, sum));
        }
        StatusCounts counts = ids.isEmpty() ? new StatusCounts(0, 0, 0, 0)
                : counts(entries.countByStatus(companyId, ids, first, last));
        return new Month(year, month, first, last, rows, dayTotals, grand, counts, page, size, total);
    }

    static List<DayCell> cells(LocalDate start, int days, List<TimeEntry> list) {
        List<DayCell> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            List<TimeEntry> mine = list.stream().filter(t -> t.getWorkDate().equals(d)).toList();
            BigDecimal hours = mine.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new DayCell(d, hours, mine.size(), dayStatus(mine)));
        }
        return out;
    }

    static WeekRow row(Employee e, LocalDate monday, List<TimeEntry> list) {
        List<DayCell> days = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            List<TimeEntry> mine = list.stream().filter(t -> t.getWorkDate().equals(d)).toList();
            BigDecimal hours = mine.stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(hours);
            days.add(new DayCell(d, hours, mine.size(), dayStatus(mine)));
        }
        return new WeekRow(e.id(), e.fullName(), e.designation() == null ? null : e.designation().name(),
                e.profileImageUrl(), days, total);
    }

    static ERegularizationStatus dayStatus(List<TimeEntry> list) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.stream().anyMatch(t -> t.getStatus() == ERegularizationStatus.PENDING)) {
            return ERegularizationStatus.PENDING;
        }
        return list.stream().anyMatch(t -> t.getStatus() == ERegularizationStatus.REJECTED)
                ? ERegularizationStatus.REJECTED : ERegularizationStatus.APPROVED;
    }

    /** Every entry of one employee's day (View All). */
    @Transactional(readOnly = true)
    public List<EntryDto> day(Long employeeId, LocalDate date) {
        Employee e = employees.get(employeeId);
        return entries.findByCompanyIdAndEmployeeIdAndWorkDateOrderByIdAsc(companyOf(e), employeeId, date).stream()
                .map(EntryDto::of).toList();
    }

    @Transactional(readOnly = true)
    public EntryDto get(Long id) {
        return EntryDto.of(require(id));
    }

    @Transactional
    public EntryDto create(EntryForm form) {
        Employee e = employees.get(form.employeeId());
        return EntryDto.of(save(new TimeEntry(), companyOf(e), form));
    }

    /** Full replacement of a pending or rejected entry; it goes back to PENDING. */
    @Transactional
    public EntryDto update(Long id, EntryForm form) {
        TimeEntry t = require(id);
        if (t.getStatus() == ERegularizationStatus.APPROVED) {
            throw ApiException.badRequest(EAttendanceError.ATT_030);
        }
        Employee e = employees.get(form.employeeId());
        t.setStatus(ERegularizationStatus.PENDING);
        t.setManagerComments(null);
        t.setDecidedAt(null);
        t.setDecidedBy(null);
        return EntryDto.of(save(t, companyOf(e), form));
    }

    @Transactional
    public EntryDto approve(Long id, String comments) {
        return EntryDto.of(decide(requirePending(id), ERegularizationStatus.APPROVED, comments));
    }

    @Transactional
    public EntryDto reject(Long id, String comments) {
        return EntryDto.of(decide(requirePending(id), ERegularizationStatus.REJECTED, comments));
    }

    @Transactional
    public void delete(Long id) {
        entries.delete(require(id));
    }

    /** Shared by the form and the import: the employee is already resolved for this company. */
    TimeEntry save(TimeEntry t, Long companyId, EntryForm f) {
        if (f.date().isAfter(LocalDate.now(clock.withZone(ZONE)))) {
            throw ApiException.badRequest(EAttendanceError.ATT_032);
        }
        BigDecimal hours = total(f);
        if (hours.compareTo(MIN_TIME) < 0) {
            throw ApiException.badRequest(EAttendanceError.ATT_053);
        }
        BigDecimal other = entries.hoursOfDay(f.employeeId(), f.date(), t.getId() == null ? -1L : t.getId());
        if (other.add(hours).compareTo(DAY_MAX) > 0) {
            throw ApiException.badRequest(EAttendanceError.ATT_031);
        }
        t.setCompanyId(companyId);
        t.setEmployeeId(f.employeeId());
        t.setWorkDate(f.date());
        t.setHours(hours);
        t.setEntryType(f.type() == null ? ETimeEntryType.MANUAL : f.type());
        t.setProject(Texts.blankToNull(f.project()));
        t.setDescription(f.description().trim());
        return entries.save(t);
    }

    private TimeEntry decide(TimeEntry t, ERegularizationStatus status, String comments) {
        t.setStatus(status);
        t.setManagerComments(Texts.blankToNull(comments));
        t.setDecidedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        t.setDecidedAt(clock.instant());
        return t;
    }

    private static StatusCounts counts(List<Object[]> rows) {
        Map<ERegularizationStatus, Long> by = new EnumMap<>(ERegularizationStatus.class);
        for (Object[] r : rows) {
            by.put((ERegularizationStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(),
                by.getOrDefault(ERegularizationStatus.PENDING, 0L), by.getOrDefault(ERegularizationStatus.APPROVED, 0L),
                by.getOrDefault(ERegularizationStatus.REJECTED, 0L));
    }

    /** The employee's own company (from its profile), checked against the caller's. */
    static Long companyOf(Employee e) {
        Long companyId = Long.valueOf(e.companyId());
        TenantGuard.assertSameCompany(companyId, EAttendanceError.ATT_029);
        return companyId;
    }

    private TimeEntry require(Long id) {
        TimeEntry t = entries.findById(id).orElseThrow(() -> ApiException.notFound(EAttendanceError.ATT_029));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAttendanceError.ATT_029);
        return t;
    }

    private TimeEntry requirePending(Long id) {
        TimeEntry t = require(id);
        if (t.getStatus() != ERegularizationStatus.PENDING) {
            throw ApiException.badRequest(EAttendanceError.ATT_020);
        }
        return t;
    }

    static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EAttendanceError.ATT_004);
    }
}
