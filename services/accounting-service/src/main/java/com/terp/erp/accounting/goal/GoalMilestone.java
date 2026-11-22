package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Status is not stored: it follows the goal's current amount, which includes computed automatic contributions. */
@Getter
@Setter
@Entity
@Table(name = "goal_milestone")
public class GoalMilestone extends BaseAuditableEntity {
    @Column(name = "goal_id", nullable = false, updatable = false)
    private Long goalId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;
}
