package com.terp.erp.payroll.payslip;

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

/** The payslip of one payroll entry (docs/analysis/06 §8.1.1); the amounts live on the entry. */
@Getter
@Setter
@Entity
@Table(name = "payslip")
public class Payslip extends BaseAuditableEntity {
    public enum EStatus { GENERATED, DOWNLOADED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    /** The month tab: the month the pay period starts in. */
    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.GENERATED;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;
}
