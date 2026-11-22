package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A balance sheet snapshot (manual 13, FR-DE-005); difference = assets - (liabilities + equity). */
@Getter
@Setter
@Entity
@Table(name = "balance_sheet")
public class BalanceSheet extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "sheet_date", nullable = false)
    private LocalDate sheetDate;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EBalanceSheetStatus status;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "total_assets", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAssets = BigDecimal.ZERO;

    @Column(name = "total_liabilities", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalLiabilities = BigDecimal.ZERO;

    @Column(name = "total_equity", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalEquity = BigDecimal.ZERO;

    @Column(name = "difference", nullable = false, precision = 19, scale = 4)
    private BigDecimal difference = BigDecimal.ZERO;

    @Column(name = "finalised_at")
    private Instant finalisedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sheet_id", nullable = false)
    @OrderBy("sortOrder")
    private List<BalanceSheetLine> lines = new ArrayList<>();
}
