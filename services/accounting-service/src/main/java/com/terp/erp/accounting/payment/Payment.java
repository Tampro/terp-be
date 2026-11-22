package com.terp.erp.accounting.payment;

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
import com.terp.erp.accounting.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A customer payment (CP-) or vendor payment (VP-). partyId = customer or vendor id. amount = cash through the bank
 * account = allocatedTotal - notesTotal (the notes settle the rest of the invoices).
 */
@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private EPaymentKind kind;

    @Column(name = "payment_number", nullable = false, length = 30)
    private String number;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "party_name", nullable = false)
    private String partyName;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "allocated_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedTotal = BigDecimal.ZERO;

    @Column(name = "notes_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal notesTotal = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EPaymentStatus status = EPaymentStatus.PENDING;

    @Column(name = "cleared_by", length = 64)
    private String clearedBy;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_id", nullable = false)
    @OrderBy("id")
    private List<PaymentAllocation> allocations = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "payment_id", nullable = false)
    @OrderBy("id")
    private List<PaymentNote> appliedNotes = new ArrayList<>();
}
