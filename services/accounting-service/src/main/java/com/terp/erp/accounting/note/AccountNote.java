package com.terp.erp.accounting.note;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.accounting.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A credit note (customer) or debit note (vendor) made from a completed return of commerce-service; balance = what is
 * still left to apply in payments. returnKey = returnId while not deleted (one live note per return).
 */
@Getter
@Setter
@Entity
@Table(name = "account_note")
public class AccountNote extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private ENoteKind kind;

    @Column(name = "note_number", nullable = false, length = 30)
    private String number;

    @Column(name = "return_id", nullable = false)
    private Long returnId;

    @Column(name = "return_key")
    private Long returnKey;

    @Column(name = "return_number", length = 30)
    private String returnNumber;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "party_name", nullable = false)
    private String partyName;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ENoteStatus status = ENoteStatus.DRAFT;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
