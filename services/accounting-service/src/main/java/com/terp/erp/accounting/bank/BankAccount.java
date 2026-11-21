package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A bank account of the company; balance = opening balance + its transactions. */
@Getter
@Setter
@Entity
@Table(name = "bank_account")
public class BankAccount extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
