package com.terp.erp.payroll.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A loan to an employee (manual 15 Set salary → Loans), repaid by an installment deducted in each payroll run while
 * it is active. remainingBalance = amount − the repayments of processed, not cancelled runs; it reaches 0 → COMPLETED.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_loan")
public class EmployeeLoan extends BaseAuditableEntity {
    public enum EStatus { ACTIVE, COMPLETED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "loan_type_id", nullable = false)
    private Long loanTypeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Deducted per payroll run. */
    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal installmentAmount;

    /** Set when the installment was derived from a number of installments. */
    @Column(name = "installments")
    private Integer installments;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null = until repaid. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "remaining_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingBalance;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
