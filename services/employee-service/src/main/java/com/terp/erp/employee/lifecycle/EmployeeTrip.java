package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A business trip with its advance and the reimbursement of its expenses (docs/analysis/06 §7.2.7). */
@Getter
@Setter
@Entity
@Table(name = "employee_trip")
public class EmployeeTrip extends BaseAuditableEntity {
    public enum EStatus { PLANNED, ONGOING, COMPLETED, CANCELLED }

    public enum EAdvanceStatus { REQUESTED, APPROVED, PAID, RECONCILED }

    public enum EReimbursementStatus { PENDING, APPROVED, PAID }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "purpose", nullable = false, length = 200)
    private String purpose;

    @Column(name = "destination", nullable = false, length = 200)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_outcomes", columnDefinition = "TEXT")
    private String expectedOutcomes;

    @Column(name = "document_media_id")
    private Long documentMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PLANNED;

    /** In the company currency; null = no advance. */
    @Column(name = "advance_amount", precision = 15, scale = 2)
    private BigDecimal advanceAmount;

    /** Null while there is no advance. */
    @Enumerated(EnumType.STRING)
    @Column(name = "advance_status", length = 12)
    private EAdvanceStatus advanceStatus;

    /** Sum of the trip's expense lines, kept in step on every change. */
    @Column(name = "expenses_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal expensesTotal = BigDecimal.ZERO;

    /** Null while there is no expense. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_status", length = 12)
    private EReimbursementStatus reimbursementStatus;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
