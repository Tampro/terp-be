package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One account (or the current-year profit, accountId null) on a balance sheet snapshot. */
@Getter
@Setter
@Entity
@Table(name = "balance_sheet_line")
public class BalanceSheetLine {
    public enum ESection { ASSETS, LIABILITIES, EQUITY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 20)
    private ESection section;

    /** CURRENT_ASSETS, OTHER_ASSETS, FIXED_ASSETS, CURRENT_LIABILITIES, LONG_TERM_LIABILITIES, EQUITY. */
    @Column(name = "group_code", nullable = false, length = 30)
    private String groupCode;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_code", length = 20)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
