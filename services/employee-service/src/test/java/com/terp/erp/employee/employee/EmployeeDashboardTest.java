package com.terp.erp.employee.employee;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeDashboardTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final OrgService orgs = mock(OrgService.class);
    private final com.terp.erp.employee.org.DepartmentRepository departments =
            mock(com.terp.erp.employee.org.DepartmentRepository.class);
    private final com.terp.erp.employee.lifecycle.EmployeePromotionRepository promotions =
            mock(com.terp.erp.employee.lifecycle.EmployeePromotionRepository.class);
    private final com.terp.erp.employee.lifecycle.EmployeeTerminationRepository terminations =
            mock(com.terp.erp.employee.lifecycle.EmployeeTerminationRepository.class);
    private final EmployeeDashboardService service = new EmployeeDashboardService(employees, branches,
            departments, promotions, terminations, orgs,
            mock(MediaClient.class), Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void figuresOfTheCompany() {
        TestLogin.as(10L, "EMPLOYEE_VIEW");
        Employee a = emp(1L, "An", EEmployeeStatus.ACTIVE, LocalDate.of(2026, 11, 3), LocalDate.of(1990, 11, 21));
        Employee b = emp(2L, "Binh", EEmployeeStatus.PROBATION, LocalDate.of(2026, 10, 15), LocalDate.of(1992, 5, 1));
        Employee c = emp(3L, "Cuong", EEmployeeStatus.TERMINATED, LocalDate.of(2025, 2, 1), LocalDate.of(1991, 11, 21));
        when(employees.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(a, b, c));
        when(branches.findByCompanyIdAndDeletedFalseOrderByNameAsc(10L)).thenReturn(List.of(new Branch(), new Branch()));
        when(orgs.names(eq(10L), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new OrgService.Names(Map.of(), Map.of(), Map.of(7L, "Developer")));

        var d = service.dashboard(null, null);
        assertThat(d.totalEmployees()).isEqualTo(3);
        assertThat(d.activeEmployees()).isEqualTo(1);
        assertThat(d.newThisMonth()).isEqualTo(1);
        assertThat(d.newLastMonth()).isEqualTo(1);
        assertThat(d.branches()).isEqualTo(2);
        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.hiringTrend()).hasSize(12);
        assertThat(d.hiringTrend().get(9)).isEqualTo(1L);
        assertThat(d.hiringTrend().get(10)).isEqualTo(1L);
        // terminated Cuong shares the birthday but is left out
        assertThat(d.birthdaysToday()).extracting(x -> x.fullName()).containsExactly("An");
        assertThat(d.birthdaysToday().get(0).designation()).isEqualTo("Developer");
        assertThat(service.dashboard(null, 2025).hiringTrend().get(1)).isEqualTo(1L);
    }

    @Test
    void departmentsPromotionsAndTerminations() {
        TestLogin.as(10L, "EMPLOYEE_VIEW");
        Employee a = emp(1L, "An", EEmployeeStatus.ACTIVE, LocalDate.of(2026, 1, 3), LocalDate.of(1990, 1, 1));
        Employee b = emp(2L, "Binh", EEmployeeStatus.ACTIVE, LocalDate.of(2026, 1, 3), LocalDate.of(1990, 1, 1));
        Employee c = emp(3L, "Cuong", EEmployeeStatus.ACTIVE, LocalDate.of(2026, 1, 3), LocalDate.of(1990, 1, 1));
        Employee d0 = emp(4L, "Dung", EEmployeeStatus.TERMINATED, LocalDate.of(2026, 1, 3), LocalDate.of(1990, 1, 1));
        a.setDepartmentId(5L);
        b.setDepartmentId(5L);
        d0.setDepartmentId(6L);
        when(employees.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(a, b, c, d0));
        when(departments.findByCompanyIdAndDeletedFalseOrderByNameAsc(10L))
                .thenReturn(List.of(new com.terp.erp.employee.org.Department(), new com.terp.erp.employee.org.Department()));
        when(orgs.names(eq(10L), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new OrgService.Names(Map.of(), Map.of(5L, "Sales"), Map.of()));
        LocalDate from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 12, 31);
        when(promotions.countByStatus(10L, null, from, to, "")).thenReturn(List.<Object[]>of(
                new Object[]{com.terp.erp.employee.lifecycle.EDecision.APPROVED, 2L},
                new Object[]{com.terp.erp.employee.lifecycle.EDecision.PENDING, 4L}));
        when(terminations.countByStatus(10L, null, from, to, "")).thenReturn(List.<Object[]>of(
                new Object[]{com.terp.erp.employee.lifecycle.EmployeeTermination.EStatus.PENDING, 1L},
                new Object[]{com.terp.erp.employee.lifecycle.EmployeeTermination.EStatus.APPROVED, 2L}));

        var d = service.dashboard(null, 2026);

        assertThat(d.departments()).isEqualTo(2);
        // terminated Dung is left out; Cuong has no department
        assertThat(d.byDepartment()).containsExactly(
                new EmployeeDashboardService.DepartmentCount("5", "Sales", 2),
                new EmployeeDashboardService.DepartmentCount(null, null, 1));
        assertThat(d.promotions()).isEqualTo(2);
        assertThat(d.terminations()).isEqualTo(3);
    }

    @Test
    void birthdayOccurrencesAcrossYearEndAndLeapDay() {
        LocalDate from = LocalDate.of(2026, 12, 20), to = LocalDate.of(2027, 1, 10);
        assertThat(EmployeeDashboardService.occurrence(LocalDate.of(1990, 1, 5), from, to)).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(EmployeeDashboardService.occurrence(LocalDate.of(1990, 12, 25), from, to)).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(EmployeeDashboardService.occurrence(LocalDate.of(1990, 6, 1), from, to)).isNull();
        assertThat(EmployeeDashboardService.occurrence(LocalDate.of(1992, 2, 29), LocalDate.of(2027, 2, 1),
                LocalDate.of(2027, 2, 28))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(EmployeeDashboardService.occurrence(LocalDate.of(1992, 2, 29), LocalDate.of(2028, 2, 1),
                LocalDate.of(2028, 3, 1))).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void periodLongerThan62DaysIsRefused() {
        TestLogin.as(10L, "EMPLOYEE_VIEW");
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(employees.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of());
        assertThat(service.birthdays(null, from, from.plusDays(61))).isEmpty();
        assertThatThrownBy(() -> service.birthdays(null, from, from.plusDays(62)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EEmployeeError.EMP_095);
    }

    private static Employee emp(Long id, String name, EEmployeeStatus status, LocalDate joined, LocalDate born) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompanyId(10L);
        e.setFullName(name);
        e.setStatus(status);
        e.setDateOfJoining(joined);
        e.setDateOfBirth(born);
        e.setDesignationId(7L);
        return e;
    }
}
