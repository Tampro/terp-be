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

/** Money moved between two bank accounts of a company; charges are paid by the source account. */
@Getter
@Setter
@Entity
@Table(name = "bank_transfer")
public class BankTransfer extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "transfer_number", length = 30, updatable = false)
    private String number;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate date;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal charges = BigDecimal.ZERO;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ETransferStatus status = ETransferStatus.PENDING;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
