package com.terp.erp.payroll.run;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.terp.erp.payroll.loan.EmployeeLoan;
import com.terp.erp.payroll.loan.EmployeeLoanRepository;
import com.terp.erp.payroll.loan.LoanRepayment;
import com.terp.erp.payroll.loan.LoanRepaymentRepository;
import com.terp.erp.payroll.overtime.EmployeeOvertime;
import com.terp.erp.payroll.overtime.EmployeeOvertimeRepository;
import com.terp.erp.payroll.overtime.EmployeeOvertimeService;
import com.terp.erp.payroll.salary.SalaryMath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual overtime and loan installments of a run, booked when the run is processed and released when it is
 * cancelled (or an entry removed), so each overtime is paid once and each loan balance matches its repayments.
 * Runs inside the caller's transaction.
 */
@Component
@RequiredArgsConstructor
public class PayrollAdjustments {
    private final EmployeeLoanRepository loans;
    private final LoanRepaymentRepository repayments;
    private final EmployeeOvertimeRepository overtimes;

    public record Amounts(BigDecimal manualOvertime, BigDecimal loanDeduction) {
        static final Amounts NONE = new Amounts(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Per employee: APPROVED, unpaid overtime overlapping the period (marked paid by the run) and the installment
     * of every ACTIVE loan overlapping the period, capped at its remaining balance (recorded as a repayment; the
     * loan COMPLETES at 0). Employees without either are absent from the map.
     */
    public Map<Long, Amounts> book(Long companyId, Long runId, LocalDate from, LocalDate to,
                                   Collection<Long> employeeIds) {
        Map<Long, BigDecimal> ot = new HashMap<>();
        Map<Long, BigDecimal> loan = new HashMap<>();
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        for (EmployeeOvertime o : overtimes.payableIn(companyId, employeeIds, from, to)) {
            // the query already filters; kept so a looser query can never pay unapproved or paid overtime
            if (o.getStatus() != EmployeeOvertime.EStatus.APPROVED || o.getPaidRunId() != null
                    || o.getStartDate().isAfter(to) || o.getEndDate().isBefore(from)) {
                continue;
            }
            ot.merge(o.getEmployeeId(), EmployeeOvertimeService.amount(o), BigDecimal::add);
            o.setPaidRunId(runId);
        }
        List<LoanRepayment> made = new ArrayList<>();
        for (EmployeeLoan l : loans.dueIn(companyId, employeeIds, from, to)) {
            if (l.getStatus() != EmployeeLoan.EStatus.ACTIVE || l.getStartDate().isAfter(to)
                    || (l.getEndDate() != null && l.getEndDate().isBefore(from))) {
                continue;
            }
            BigDecimal due = l.getInstallmentAmount().min(l.getRemainingBalance());
            if (due.signum() <= 0) {
                continue;
            }
            l.setRemainingBalance(l.getRemainingBalance().subtract(due));
            if (l.getRemainingBalance().signum() == 0) {
                l.setStatus(EmployeeLoan.EStatus.COMPLETED);
            }
            LoanRepayment r = new LoanRepayment();
            r.setCompanyId(companyId);
            r.setLoanId(l.getId());
            r.setRunId(runId);
            r.setEmployeeId(l.getEmployeeId());
            r.setAmount(due);
            made.add(r);
            loan.merge(l.getEmployeeId(), due, BigDecimal::add);
        }
        if (!made.isEmpty()) {
            repayments.saveAll(made);
        }
        Map<Long, Amounts> out = new HashMap<>();
        for (Long id : employeeIds) {
            BigDecimal o = ot.get(id);
            BigDecimal d = loan.get(id);
            if (o != null || d != null) {
                out.put(id, new Amounts(o == null ? BigDecimal.ZERO : SalaryMath.round(o),
                        d == null ? BigDecimal.ZERO : SalaryMath.round(d)));
            }
        }
        return out;
    }

    public static Amounts of(Map<Long, Amounts> booked, Long employeeId) {
        return booked.getOrDefault(employeeId, Amounts.NONE);
    }

    /** The whole run is cancelled. */
    public void release(Long runId) {
        undo(repayments.findByRunId(runId), overtimes.findByPaidRunId(runId));
    }

    /** One employee's entry is removed from the run. */
    public void release(Long runId, Long employeeId) {
        undo(repayments.findByRunIdAndEmployeeId(runId, employeeId),
                overtimes.findByPaidRunIdAndEmployeeId(runId, employeeId));
    }

    private void undo(List<LoanRepayment> paid, List<EmployeeOvertime> ots) {
        for (LoanRepayment r : paid) {
            loans.findById(r.getLoanId()).ifPresent(l -> {
                l.setRemainingBalance(l.getRemainingBalance().add(r.getAmount()));
                l.setStatus(EmployeeLoan.EStatus.ACTIVE);
            });
        }
        if (!paid.isEmpty()) {
            repayments.deleteAll(paid);
        }
        ots.forEach(o -> o.setPaidRunId(null));
    }
}
