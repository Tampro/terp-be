package com.terp.erp.payroll.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;

/** The installment of a loan deducted by one payroll run; removed when the run is cancelled. */
@Getter
@Setter
@Entity
@Table(name = "loan_repayment")
public class LoanRepayment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
