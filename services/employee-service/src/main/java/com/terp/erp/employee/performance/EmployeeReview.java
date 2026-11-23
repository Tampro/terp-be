package com.terp.erp.employee.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A performance review of an employee in a review cycle (docs/analysis/06 §7.3.1). */
@Getter
@Setter
@Entity
@Table(name = "employee_review")
public class EmployeeReview extends BaseAuditableEntity {
    /** Manual 16: Pending -> In Progress -> Completed (SCHEDULED rows were migrated to PENDING, 20261122800100). */
    public enum EStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "review_cycle_id", nullable = false)
    private Long reviewCycleId;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PENDING;

    /** Weighted average of the rated indicators, 1 decimal; null until something is rated. */
    @Column(name = "overall_rating", precision = 3, scale = 1)
    private BigDecimal overallRating;

    @Column(name = "overall_comments", columnDefinition = "TEXT")
    private String overallComments;

    @Column(name = "pros", columnDefinition = "TEXT")
    private String pros;

    @Column(name = "cons", columnDefinition = "TEXT")
    private String cons;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
