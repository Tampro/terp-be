package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Last number issued per (company, key) where key is the prefix and period, e.g. JE-2026 or REV-2026-11. */
@Getter
@Setter
@Entity
@Table(name = "accounting_sequence")
public class DocumentSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "seq_key", nullable = false, length = 30, updatable = false)
    private String seqKey;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
