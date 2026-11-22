package com.terp.erp.payroll.run;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.TestLogin;
import com.terp.erp.payroll.client.AccountingClient;
import com.terp.erp.payroll.client.AttendanceClient;
import com.terp.erp.payroll.client.AttendanceClient.Summary;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.component.SalaryComponentRepository;
import com.terp.erp.payroll.payslip.PayslipRepository;
import com.terp.erp.payroll.run.PayrollAdjustments.Amounts;
import com.terp.erp.payroll.run.PayrollRun.EPaymentStatus;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.run.PayrollRunService.PayForm;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollPayAndExclusionTest {
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 12, 5);

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayrollEntryRepository entries = mock(PayrollEntryRepository.class);
    private final EmployeeSalaryRepository salaries = mock(EmployeeSalaryRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final AttendanceClient attendance = mock(AttendanceClient.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final PayrollRunExclusionRepository exclusions = mock(PayrollRunExclusionRepository.class);
    private final PayrollAdjustments adjustments = mock(PayrollAdjustments.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final PayrollRunService service = new PayrollRunService(runs, entries, salaries, components, attendance,
            mock(EmployeeClient.class), payslips, Clock.fixed(Instant.parse("2026-12-05T08:00:00Z"), ZoneOffset.UTC),
            exclusions, adjustments, accounting);

    private final PayrollRun run = new PayrollRun();
    private final List<PayrollEntry> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PAYROLL_MANAGE", "PAYROLL_PAY");
        run.setId(5L);
        run.setCompanyId(10L);
        run.setTitle("November 2026");
        run.setPeriodStart(LocalDate.of(2026, 11, 1));
        run.setPeriodEnd(LocalDate.of(2026, 11, 30));
        run.setPayDate(PAY_DATE);
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
    void excludedEmployeeGetsNoEntryAndIsNotInTotals() {
        when(salaries.findByCompanyIdAndStatusAndDeletedFalse(10L, EmployeeSalary.EStatus.ACTIVE))
                .thenReturn(List.of(salary(1L, 7L), salary(2L, 8L)));
        PayrollRunExclusion x = new PayrollRunExclusion();
        x.setEmployeeId(8L);
        when(exclusions.findByRunId(5L)).thenReturn(List.of(x));
        when(attendance.summaries(eq(10L), any(), any(), anyList())).thenReturn(Map.of(
                7L, summary("7"), 8L, summary("8")));
        // employee 7: 500,000 manual overtime, 1,000,000 loan installment
        when(adjustments.book(eq(10L), eq(5L), any(), any(), anyList()))
                .thenReturn(Map.of(7L, new Amounts(new BigDecimal("500000"), new BigDecimal("1000000"))));

        var dto = service.process(5L);

        assertThat(saved).extracting(PayrollEntry::getEmployeeId).containsExactly(7L);
        PayrollEntry e = saved.get(0);
        assertThat(e.getManualOvertimeAmount()).isEqualByComparingTo("500000");
        assertThat(e.getLoanDeduction()).isEqualByComparingTo("1000000");
        assertThat(e.getGrossPay()).isEqualByComparingTo("10500000");
        assertThat(e.getNetPay()).isEqualByComparingTo("9500000");
        assertThat(dto.employeeCount()).isEqualTo(1);
        assertThat(dto.grossPay()).isEqualByComparingTo("10500000");
        assertThat(dto.totalDeductions()).isEqualByComparingTo("1000000");
        assertThat(dto.netPay()).isEqualByComparingTo("9500000");
        verify(attendance).summaries(eq(10L), any(), any(), eq(List.of(7L)));
    }

    @Test
    void excludeOnlyOnDraft() {
        service.exclude(5L, 8L);
        verify(exclusions).save(any());
        run.setStatus(EStatus.COMPLETED);
        assertCode(() -> service.exclude(5L, 9L), EPayrollError.PAY_013);
    }

    @Test
    void payOnlyCompletedAndUnpaidCallsAccountingOnceThenRefuses() {
        run.setBankAccountId(3L);
        run.setNetPay(new BigDecimal("9500000"));
        assertCode(() -> service.pay(5L, null), EPayrollError.PAY_036);
        verify(accounting, never()).payrollPosting(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());

        run.setStatus(EStatus.COMPLETED);
        var dto = service.pay(5L, null);

        assertThat(dto.paymentStatus()).isEqualTo(EPaymentStatus.PAID);
        assertThat(dto.paidAt()).isEqualTo(Instant.parse("2026-12-05T08:00:00Z"));
        assertThat(dto.paidBy()).isEqualTo("kc-5");
        assertCode(() -> service.pay(5L, null), EPayrollError.PAY_037);
        assertCode(() -> service.cancel(5L), EPayrollError.PAY_042);
        verify(accounting, times(1)).payrollPosting(eq(10L), eq(5L), eq(3L),
                eq(new BigDecimal("9500000")), eq(PAY_DATE), eq("PAYROLL-5"), any(), eq(LocalDate.of(2026, 11, 30)));
    }

    @Test
    void payNeedsABankAccountAndANetPay() {
        run.setStatus(EStatus.COMPLETED);
        run.setNetPay(new BigDecimal("100"));
        assertCode(() -> service.pay(5L, null), EPayrollError.PAY_038);
        run.setNetPay(BigDecimal.ZERO);
        assertCode(() -> service.pay(5L, new PayForm(3L, null)), EPayrollError.PAY_040);
        verify(accounting, never()).payrollPosting(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void otherCompanysRunIsNotFoundForPayAndExclusions() {
        run.setCompanyId(11L);
        run.setStatus(EStatus.COMPLETED);
        assertCode(() -> service.pay(5L, new PayForm(3L, null)), EPayrollError.PAY_012);
        assertCode(() -> service.exclude(5L, 7L), EPayrollError.PAY_012);
        assertCode(() -> service.exclusions(5L), EPayrollError.PAY_012);
        verify(accounting, never()).payrollPosting(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void cancellingACompletedRunReleasesLoansAndOvertime() {
        run.setStatus(EStatus.COMPLETED);
        service.cancel(5L);
        verify(adjustments).release(5L);
    }

    private static Summary summary(String id) {
        return new Summary(id, 20, new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private static EmployeeSalary salary(Long id, Long employeeId) {
        EmployeeSalary s = new EmployeeSalary();
        s.setId(id);
        s.setCompanyId(10L);
        s.setEmployeeId(employeeId);
        s.setEmployeeName("Test " + employeeId);
        s.setBasicSalary(new BigDecimal("10000000"));
        return s;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EPayrollError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
