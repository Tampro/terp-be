package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A manual contribution. Automatic ones are computed from the ledger and never stored. */
@Getter
@Setter
@Entity
@Table(name = "goal_contribution")
public class GoalContribution extends BaseAuditableEntity {
    @Column(name = "goal_id", nullable = false, updatable = false)
    private Long goalId;

    @Column(name = "contribution_date", nullable = false)
    private LocalDate date;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "notes", length = 500)
    private String notes;
}
