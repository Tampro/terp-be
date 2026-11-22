package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Amount planned on one expense account; spent and remaining are computed from the ledger on read. */
@Getter
@Setter
@Entity
@Table(name = "budget_allocation")
public class BudgetAllocation extends BaseAuditableEntity {
    @Column(name = "budget_id", nullable = false, updatable = false)
    private Long budgetId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocated;

    @Column(name = "notes", length = 500)
    private String notes;
}
