package com.terp.erp.payroll.run;

import org.junit.jupiter.api.Test;
import com.terp.erp.payroll.loan.EmployeeLoan;
import com.terp.erp.payroll.loan.EmployeeLoanRepository;
import com.terp.erp.payroll.loan.LoanRepayment;
import com.terp.erp.payroll.loan.LoanRepaymentRepository;
import com.terp.erp.payroll.overtime.EmployeeOvertime;
import com.terp.erp.payroll.overtime.EmployeeOvertimeRepository;
import com.terp.erp.payroll.run.PayrollAdjustments.Amounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollAdjustmentsTest {
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    private final EmployeeLoanRepository loans = mock(EmployeeLoanRepository.class);
    private final LoanRepaymentRepository repayments = mock(LoanRepaymentRepository.class);
    private final EmployeeOvertimeRepository overtimes = mock(EmployeeOvertimeRepository.class);
    private final PayrollAdjustments adjustments = new PayrollAdjustments(loans, repayments, overtimes);

    @Test
    void loanDeductionIsCappedAtRemainingAndCompletesTheLoan() {
        EmployeeLoan l = loan(1L, "1000000", "400000", LocalDate.of(2026, 1, 1), null);
        when(loans.dueIn(eq(10L), any(), eq(FROM), eq(TO))).thenReturn(List.of(l));

        Map<Long, Amounts> out = adjustments.book(10L, 5L, FROM, TO, List.of(7L));

        assertThat(out.get(7L).loanDeduction()).isEqualByComparingTo("400000");
        assertThat(l.getRemainingBalance()).isEqualByComparingTo("0");
        assertThat(l.getStatus()).isEqualTo(EmployeeLoan.EStatus.COMPLETED);
        verify(repayments).saveAll(anyList());
    }

    @Test
    void loanEndedBeforeThePeriodOrNotStartedDeductsNothing() {
        EmployeeLoan ended = loan(1L, "500000", "5000000", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31));
        EmployeeLoan later = loan(2L, "500000", "5000000", LocalDate.of(2026, 12, 1), null);
        when(loans.dueIn(eq(10L), any(), eq(FROM), eq(TO))).thenReturn(List.of(ended, later));

        Map<Long, Amounts> out = adjustments.book(10L, 5L, FROM, TO, List.of(7L));

        assertThat(out).doesNotContainKey(7L);
        assertThat(ended.getRemainingBalance()).isEqualByComparingTo("5000000");
        assertThat(later.getRemainingBalance()).isEqualByComparingTo("5000000");
    }

    @Test
    void onlyApprovedUnpaidOvertimeCountsAndIsMarkedPaid() {
        EmployeeOvertime approved = overtime(1L, EmployeeOvertime.EStatus.APPROVED);
        EmployeeOvertime pending = overtime(2L, EmployeeOvertime.EStatus.PENDING);
        EmployeeOvertime rejected = overtime(3L, EmployeeOvertime.EStatus.REJECTED);
        when(overtimes.payableIn(eq(10L), any(), eq(FROM), eq(TO))).thenReturn(List.of(approved, pending, rejected));

        Map<Long, Amounts> out = adjustments.book(10L, 5L, FROM, TO, List.of(7L));

        // 2 days × 3 hours × 150,000 = 900,000, the pending and rejected ones left out
        assertThat(out.get(7L).manualOvertime()).isEqualByComparingTo("900000");
        assertThat(approved.getPaidRunId()).isEqualTo(5L);
        assertThat(pending.getPaidRunId()).isNull();
        assertThat(rejected.getPaidRunId()).isNull();
    }

    @Test
    void releaseRestoresTheLoanAndUnpaysTheOvertime() {
        EmployeeLoan l = loan(1L, "400000", "0", LocalDate.of(2026, 1, 1), null);
        l.setStatus(EmployeeLoan.EStatus.COMPLETED);
        LoanRepayment r = new LoanRepayment();
        r.setLoanId(1L);
        r.setAmount(new BigDecimal("400000"));
        EmployeeOvertime o = overtime(1L, EmployeeOvertime.EStatus.APPROVED);
        o.setPaidRunId(5L);
        when(repayments.findByRunId(5L)).thenReturn(List.of(r));
        when(loans.findById(1L)).thenReturn(Optional.of(l));
        when(overtimes.findByPaidRunId(5L)).thenReturn(List.of(o));

        adjustments.release(5L);

        assertThat(l.getRemainingBalance()).isEqualByComparingTo("400000");
        assertThat(l.getStatus()).isEqualTo(EmployeeLoan.EStatus.ACTIVE);
        assertThat(o.getPaidRunId()).isNull();
        verify(repayments).deleteAll(List.of(r));
    }

    private static EmployeeLoan loan(Long id, String installment, String remaining, LocalDate start, LocalDate end) {
        EmployeeLoan l = new EmployeeLoan();
        l.setId(id);
        l.setCompanyId(10L);
        l.setEmployeeId(7L);
        l.setAmount(new BigDecimal("10000000"));
        l.setInstallmentAmount(new BigDecimal(installment));
        l.setRemainingBalance(new BigDecimal(remaining));
        l.setStartDate(start);
        l.setEndDate(end);
        return l;
    }

    private static EmployeeOvertime overtime(Long id, EmployeeOvertime.EStatus status) {
        EmployeeOvertime o = new EmployeeOvertime();
        o.setId(id);
        o.setCompanyId(10L);
        o.setEmployeeId(7L);
        o.setDays(new BigDecimal("2"));
        o.setHours(new BigDecimal("3"));
        o.setRate(new BigDecimal("150000"));
        o.setStartDate(LocalDate.of(2026, 11, 20));
        o.setEndDate(LocalDate.of(2026, 11, 21));
        o.setStatus(status);
        return o;
    }
}
