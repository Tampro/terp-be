package com.terp.erp.payroll.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

/**
 * The HRM setup catalogues of manual 15 "Setup": loan, allowance and deduction types. One table, told apart by kind;
 * code is unique per company and kind.
 * TODO(assumption): allowance and deduction types are labels only; the amounts of a salary are still salary
 * components (EARNING / DEDUCTION). Linking a component to a type is left until BA confirms it is wanted.
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_type")
public class PayrollType extends BaseAuditableEntity {
    public enum EKind { LOAN, ALLOWANCE, DEDUCTION }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 12)
    private EKind kind;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
