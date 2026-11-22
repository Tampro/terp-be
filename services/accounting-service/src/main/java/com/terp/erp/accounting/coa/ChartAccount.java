package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A ledger account. currentBalance = openingBalance + posted journal lines on the account's normal side, kept by
 * the journal under a row lock.
 */
@Getter
@Setter
@Entity
@Table(name = "chart_account")
public class ChartAccount extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "account_type_id", nullable = false)
    private Long accountTypeId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private ENormalBalance normalBalance;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "description", length = 500)
    private String description;
}
