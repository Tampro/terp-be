package com.terp.erp.payroll.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A pay cycle of the company (docs/analysis/06 §8.1.2). */
@Getter
@Setter
@Entity
@Table(name = "payroll_run")
public class PayrollRun extends BaseAuditableEntity {
    /** PROCESSING is listed by the spec; processing runs in one request, so a run is never seen in it yet. */
    public enum EStatus { DRAFT, PROCESSING, COMPLETED, CANCELLED }

    public enum EFrequency { MONTHLY, SEMI_MONTHLY, BI_WEEKLY, WEEKLY }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private EFrequency frequency;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "gross_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPay = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
