package com.terp.erp.payroll.run;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.client.AttendanceClient;
import com.terp.erp.payroll.client.AttendanceClient.Summary;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.component.SalaryComponent;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;
import com.terp.erp.payroll.component.SalaryComponentRepository;
import com.terp.erp.payroll.run.PayrollRun.EFrequency;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.run.PayrollRunService.RunForm;
import com.terp.erp.payroll.salary.EmployeeSalary;
import com.terp.erp.payroll.salary.EmployeeSalaryRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayrollRunServiceTest {
    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollEntryRepository entries = mock(PayrollEntryRepository.class);
    private final EmployeeSalaryRepository salaries = mock(EmployeeSalaryRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final AttendanceClient attendance = mock(AttendanceClient.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final com.terp.erp.payroll.payslip.PayslipRepository payslips =
            mock(com.terp.erp.payroll.payslip.PayslipRepository.class);
    private final PayrollRunExclusionRepository exclusions = mock(PayrollRunExclusionRepository.class);
    private final PayrollAdjustments adjustments = mock(PayrollAdjustments.class);
    private final com.terp.erp.payroll.client.AccountingClient accounting =
            mock(com.terp.erp.payroll.client.AccountingClient.class);
    private final PayrollRunService service = new PayrollRunService(runs, entries, salaries, components, attendance,
            employees, payslips, Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC), exclusions,
            adjustments, accounting);

    private final PayrollRun run = new PayrollRun();
    private final List<PayrollEntry> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE");
        run.setId(5L);
        run.setCompanyId(10L);
        run.setPeriodStart(LocalDate.of(2026, 11, 1));
        run.setPeriodEnd(LocalDate.of(2026, 11, 30));
        when(runs.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(run));
        when(entries.saveAll(anyList())).thenAnswer(i -> {
            saved.addAll(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processCalculatesEntriesAndCompletes() {
        when(salaries.findByCompanyIdAndStatusAndDeletedFalse(10L, EmployeeSalary.EStatus.ACTIVE))
                .thenReturn(List.of(salary(1L, 7L), salary(2L, 8L)));
        when(components.findAllById(any())).thenReturn(List.of(
                component(1L, EType.EARNING, ECalculation.PERCENTAGE, "40"),
                component(3L, EType.DEDUCTION, ECalculation.FIXED, "1000000")));
        // employee 8 is no longer in the company: attendance-service leaves it out
        when(attendance.summaries(eq(10L), eq(run.getPeriodStart()), eq(run.getPeriodEnd()), anyList()))
                .thenReturn(Map.of(7L, new Summary("7", 20, new BigDecimal("17"), BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("300000"), List.of())));
        var dto = service.process(5L);

        assertThat(saved).hasSize(1);
        PayrollEntry e = saved.get(0);
        // basic 20,000,000 + HRA 40% 8,000,000 = 28,000,000; LOP and unpaid 1 day each = 1,000,000 each
        assertThat(e.getTotalEarnings()).isEqualByComparingTo("28000000");
        assertThat(e.getLopDeduction()).isEqualByComparingTo("1000000");
        assertThat(e.getUnpaidLeaveDeduction()).isEqualByComparingTo("1000000");
        assertThat(e.getGrossPay()).isEqualByComparingTo("26300000");
        assertThat(e.getTotalDeductions()).isEqualByComparingTo("1000000");
        assertThat(e.getNetPay()).isEqualByComparingTo("25300000");
        assertThat(e.getLines()).hasSize(2);
        assertThat(dto.status()).isEqualTo(EStatus.COMPLETED);
        assertThat(dto.employeeCount()).isEqualTo(1);
        assertThat(dto.netPay()).isEqualByComparingTo("25300000");
        assertThat(dto.processedAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void stateAndFormRules() {
        when(salaries.findByCompanyIdAndStatusAndDeletedFalse(10L, EmployeeSalary.EStatus.ACTIVE)).thenReturn(List.of());
        assertCode(() -> service.process(5L), EPayrollError.PAY_015);
        assertCode(() -> service.update(5L, form(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 5))), EPayrollError.PAY_016);
        assertCode(() -> service.update(5L, form(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 15),
                LocalDate.of(2027, 1, 20))), EPayrollError.PAY_016);
        assertCode(() -> service.update(5L, form(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 10, 31))), EPayrollError.PAY_017);
        assertCode(() -> service.deleteEntry(5L, 1L), EPayrollError.PAY_014);
        run.setStatus(EStatus.COMPLETED);
        assertCode(() -> service.process(5L), EPayrollError.PAY_013);
        assertCode(() -> service.delete(5L), EPayrollError.PAY_013);
        when(payslips.existsByRunId(5L)).thenReturn(true);
        assertCode(() -> service.cancel(5L), EPayrollError.PAY_025);
        assertCode(() -> service.deleteEntry(5L, 1L), EPayrollError.PAY_025);
        when(payslips.existsByRunId(5L)).thenReturn(false);
        assertThat(service.cancel(5L).status()).isEqualTo(EStatus.CANCELLED);
        assertCode(() -> service.cancel(5L), EPayrollError.PAY_023);
        run.setCompanyId(11L);
        assertCode(() -> service.get(5L), EPayrollError.PAY_012);
    }

    @Test
    void noWorkingDaysDeductsNothingPerDay() {
        assertThat(PayrollMath.perDay(new BigDecimal("1000"), 0, BigDecimal.ONE)).isEqualByComparingTo("0");
        // 10,000,000 / 22 × 1.5 = 681,818.18 -> 681,818
        assertThat(PayrollMath.perDay(new BigDecimal("10000000"), 22, new BigDecimal("1.5")))
                .isEqualByComparingTo("681818");
    }

    private static RunForm form(LocalDate start, LocalDate end, LocalDate pay) {
        return new RunForm("November 2026", EFrequency.MONTHLY, start, end, pay, null);
    }

    private static EmployeeSalary salary(Long id, Long employeeId) {
        EmployeeSalary s = new EmployeeSalary();
        s.setId(id);
        s.setCompanyId(10L);
        s.setEmployeeId(employeeId);
        s.setEmployeeName("Test " + employeeId);
        s.setBasicSalary(new BigDecimal("20000000"));
        s.getLines().add(new EmployeeSalary.Line(1L, null));
        s.getLines().add(new EmployeeSalary.Line(3L, null));
        return s;
    }

    private static SalaryComponent component(Long id, EType type, ECalculation calc, String value) {
        SalaryComponent c = new SalaryComponent();
        c.setId(id);
        c.setCompanyId(10L);
        c.setName("C" + id);
        c.setType(type);
        c.setCalculation(calc);
        c.setValue(new BigDecimal(value));
        return c;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
