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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A balanced journal entry; (company, sourceType, sourceId) is unique so a posting is never journalled twice. */
@Getter
@Setter
@Entity
@Table(name = "journal_entry")
public class JournalEntry extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "entry_number", nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EJournalType type;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "source_type", length = 30, updatable = false)
    private String sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EJournalStatus status;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    /** Manual entries only; see EReferenceType. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 30)
    private EReferenceType referenceType;

    /** Set on a reversal entry: the entry it cancels. */
    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    /** Set on a REVERSED entry: the reversal that cancels it. */
    @Column(name = "reversed_by_id")
    private Long reversedById;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "entry_id", nullable = false)
    @OrderBy("id")
    private List<JournalLine> lines = new ArrayList<>();
}
