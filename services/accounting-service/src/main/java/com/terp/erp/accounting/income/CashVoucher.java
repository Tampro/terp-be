package com.terp.erp.accounting.income;

import com.terp.erp.accounting.coa.ECategoryKind;
import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A revenue (money in) or an expense (money out) through a bank account; kind tells which. */
@Getter
@Setter
@Entity
@Table(name = "cash_voucher")
public class CashVoucher extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    private ECategoryKind kind;

    @Column(name = "voucher_number", nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate date;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    /** Ledger account credited (revenue) or debited (expense); defaults to the category's GL account. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EVoucherStatus status = EVoucherStatus.DRAFT;

    /** IAM user id of the approver. */
    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
