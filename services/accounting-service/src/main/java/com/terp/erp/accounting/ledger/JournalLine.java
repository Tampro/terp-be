package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One side of a journal entry: either debit or credit is > 0, the other is 0. */
@Getter
@Setter
@Entity
@Table(name = "journal_line")
public class JournalLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal credit = BigDecimal.ZERO;
}
