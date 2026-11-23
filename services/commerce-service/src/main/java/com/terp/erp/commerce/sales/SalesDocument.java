package com.terp.erp.commerce.sales;

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
import com.terp.erp.commerce.common.BaseAuditableEntity;
import com.terp.erp.commerce.common.EJournalStatus;
import com.terp.erp.commerce.common.JournalTracked;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A quotation, proposal or sales invoice of a company. Only a DRAFT is edited; totals are recomputed from the lines
 * on every save. customerId / warehouseId point to accounting- and inventory-service.
 */
@Getter
@Setter
@Entity
@Table(name = "sales_document")
public class SalesDocument extends BaseAuditableEntity implements JournalTracked {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 12)
    private EDocumentKind kind;

    @Column(name = "doc_number", nullable = false, length = 30)
    private String number;

    /** Invoices only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", length = 10)
    private EInvoiceType invoiceType;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Copied from accounting-service when saved, for lists without a call per row. */
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    /**
     * Copied from accounting-service with the name: the daily payment reminder runs without a user token and cannot
     * ask accounting for it. TODO(assumption): a later change of the customer's email is not reflected here.
     */
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Sync to Google Calendar (ERP manual: sales invoice form): notification-service adds the due date on posting. */
    @Column(name = "sync_to_google_calendar", nullable = false)
    private boolean syncToGoogleCalendar;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EDocumentStatus status = EDocumentStatus.DRAFT;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /** Invoices: cleared payments (accounting, later). */
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Quotations: the first version this one is a new version of. */
    @Column(name = "version_of_id")
    private Long versionOfId;

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    /** Quotations / proposals: the invoice made from it (one conversion only). */
    @Column(name = "converted_invoice_id")
    private Long convertedInvoiceId;

    /** Invoices: the quotation or proposal it came from. */
    @Column(name = "converted_from_id")
    private Long convertedFromId;

    @Column(name = "posted_at")
    private Instant postedAt;

    /** Its accounting journal, written asynchronously (outbox): PENDING until accounting answers, then POSTED or
     * FAILED with the accounting error code. null = a draft, nothing to journal, or posted before the outbox. */
    @Enumerated(EnumType.STRING)
    @Column(name = "journal_status", length = 10)
    private EJournalStatus journalStatus;

    @Column(name = "journal_error_code", length = 30)
    private String journalErrorCode;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("position")
    private List<SalesDocumentLine> lines = new ArrayList<>();
}
