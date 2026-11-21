package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A money movement on a bank account; (company, sourceType, sourceId) is unique so retries are idempotent. */
@Getter
@Setter
@Entity
@Table(name = "bank_transaction")
public class BankTransaction extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private Long bankAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ETransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "description", length = 500)
    private String description;
}
