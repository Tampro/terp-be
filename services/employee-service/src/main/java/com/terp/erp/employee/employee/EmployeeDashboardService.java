package com.terp.erp.employee.employee;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Employee figures of the company dashboard (docs/analysis/06 §5.1) and the birthdays of the calendar (§5.2).
 * Computed in memory from the company's employees: a company has hundreds of rows, not millions.
 */
@Service
@RequiredArgsConstructor
public class EmployeeDashboardService {
    static final int MAX_DAYS = 62;

    private final EmployeeRepository employees;
    private final BranchRepository branches;
    private final OrgService orgs;
    private final MediaClient media;
    private final Clock clock;

    public record BirthdayDto(String employeeId, String fullName, String designation, String profileImageUrl,
                              LocalDate date) {
    }

    public record EmployeeDashboard(long totalEmployees, long activeEmployees, long newThisMonth, long newLastMonth,
                                    long branches, List<BirthdayDto> birthdaysToday, int year,
                                    List<Long> hiringTrend) {
    }

    @Transactional(readOnly = true)
    public EmployeeDashboard dashboard(Long requestedCompanyId, Integer year) {
        Long companyId = EmployeeService.company(requestedCompanyId);
        LocalDate today = LocalDate.now(clock);
        int y = year == null ? today.getYear() : year;
        List<Employee> all = employees.findByCompanyIdAndDeletedFalse(companyId);
        YearMonth thisMonth = YearMonth.from(today);
        long newThis = all.stream().filter(e -> joinedIn(e, thisMonth)).count();
        long newLast = all.stream().filter(e -> joinedIn(e, thisMonth.minusMonths(1))).count();
        List<Long> trend = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(y, m);
            trend.add(all.stream().filter(e -> joinedIn(e, ym)).count());
        }
        return new EmployeeDashboard(all.size(),
                all.stream().filter(e -> e.getStatus() == EEmployeeStatus.ACTIVE).count(), newThis, newLast,
                branches.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).size(),
                birthdays(companyId, all, today, today, true), y, trend);
    }

    /** Birthdays in [from, to] (at most 62 days), by date then name; without photos. */
    @Transactional(readOnly = true)
    public List<BirthdayDto> birthdays(Long requestedCompanyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EEmployeeError.EMP_095, MAX_DAYS);
        }
        Long companyId = EmployeeService.company(requestedCompanyId);
        return birthdays(companyId, employees.findByCompanyIdAndDeletedFalse(companyId), from, to, false);
    }

    private List<BirthdayDto> birthdays(Long companyId, List<Employee> all, LocalDate from, LocalDate to,
                                        boolean withPhoto) {
        record Hit(Employee e, LocalDate date) {
        }
        List<Hit> hits = new ArrayList<>();
        for (Employee e : all) {
            if (e.getDateOfBirth() == null || e.getStatus() == EEmployeeStatus.TERMINATED) {
                continue;
            }
            LocalDate d = occurrence(e.getDateOfBirth(), from, to);
            if (d != null) {
                hits.add(new Hit(e, d));
            }
        }
        if (hits.isEmpty()) {
            return List.of();
        }
        var desIds = hits.stream().map(h -> h.e().getDesignationId()).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> designations = orgs.names(companyId, List.of(), List.of(), desIds).designations();
        return hits.stream()
                .sorted(Comparator.comparing(Hit::date).thenComparing(h -> h.e().getFullName()))
                .map(h -> new BirthdayDto(h.e().getId().toString(), h.e().getFullName(),
                        h.e().getDesignationId() == null ? null : designations.get(h.e().getDesignationId()),
                        withPhoto ? photo(h.e()) : null, h.date()))
                .toList();
    }

    /** The day in [from, to] that is this birthday; Feb 29 falls on Feb 28 in other years. */
    static LocalDate occurrence(LocalDate birth, LocalDate from, LocalDate to) {
        MonthDay md = MonthDay.from(birth);
        for (int y = from.getYear(); y <= to.getYear(); y++) {
            LocalDate d = md.isValidYear(y) ? md.atYear(y) : LocalDate.of(y, 2, 28);
            if (!d.isBefore(from) && !d.isAfter(to)) {
                return d;
            }
        }
        return null;
    }

    private String photo(Employee e) {
        var f = media.find(e.getProfileMediaId());
        return f == null ? null : f.viewUrl();
    }

    private static boolean joinedIn(Employee e, YearMonth ym) {
        return e.getDateOfJoining() != null && YearMonth.from(e.getDateOfJoining()).equals(ym);
    }
}
