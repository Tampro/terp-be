package com.terp.erp.payroll.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;

/** An earning or deduction used in salary structures (docs/analysis/06 §8.1.4). */
@Getter
@Setter
@Entity
@Table(name = "salary_component")
public class SalaryComponent extends BaseAuditableEntity {
    public enum EType { EARNING, DEDUCTION }

    /** FIXED = an amount in the company currency; PERCENTAGE = % of the basic salary. */
    public enum ECalculation { FIXED, PERCENTAGE }

    public enum EStatus { ACTIVE, INACTIVE }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private EType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation", nullable = false, length = 12)
    private ECalculation calculation;

    /** The amount (FIXED) or the percentage (PERCENTAGE). */
    @Column(name = "value", nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
