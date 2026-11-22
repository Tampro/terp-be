package com.terp.erp.payroll.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    List<LoanRepayment> findByRunId(Long runId);

    List<LoanRepayment> findByRunIdAndEmployeeId(Long runId, Long employeeId);

    boolean existsByLoanId(Long loanId);

    @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.loanId = :loanId")
    BigDecimal repaid(Long loanId);
}
