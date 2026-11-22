package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "budget_period")
public class BudgetPeriod extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "financial_year", nullable = false)
    private Integer financialYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EBudgetPeriodStatus status = EBudgetPeriodStatus.ACTIVE;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
