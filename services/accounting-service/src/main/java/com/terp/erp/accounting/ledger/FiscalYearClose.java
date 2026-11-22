package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/** A closed financial year; (company, year) unique and never deleted, so a close cannot be undone. */
@Getter
@Setter
@Entity
@Table(name = "fiscal_year_close")
@EntityListeners(AuditingEntityListener.class)
public class FiscalYearClose {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;

    @Column(name = "closing_date", nullable = false, updatable = false)
    private LocalDate closingDate;

    /** Null when the year had no revenue or expense balance to close. */
    @Column(name = "entry_id", updatable = false)
    private Long entryId;

    @CreatedBy
    @Column(name = "closed_by", length = 64, updatable = false)
    private String closedBy;

    @CreatedDate
    @Column(name = "closed_at", updatable = false)
    private Instant closedAt;
}
