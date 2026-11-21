package com.terp.erp.commerce.purchase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.commerce.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A purchase invoice from a vendor (ERP manual 8.1): DRAFT → POSTED → PARTIAL → PAID (payments come with
 * accounting). Posting puts the stocked lines into the receiving warehouse. vendorId / warehouseId point to
 * accounting- and inventory-service.
 */
@Getter
@Setter
@Entity
@Table(name = "purchase_invoice")
public class PurchaseInvoice extends BaseAuditableEntity {
    public enum EStatus { DRAFT, POSTED, PARTIAL, PAID }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "invoice_number", nullable = false, length = 30)
    private String number;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** Copied from accounting-service when saved, for lists without a call per row. */
    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    /** Receiving warehouse; required to post when a line is stocked. */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "invoice_id", nullable = false)
    @OrderBy("position")
    private List<Line> lines = new ArrayList<>();

    /** One line; item name, sku, type and tax are a copy taken when the line was saved. */
    @Getter
    @Setter
    @Entity
    @Table(name = "purchase_invoice_line")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "position", nullable = false)
        private int position;

        @Column(name = "item_id", nullable = false)
        private Long itemId;

        @Column(name = "item_type", nullable = false, length = 10)
        private String itemType;

        @Column(name = "item_name", nullable = false)
        private String itemName;

        @Column(name = "sku", length = 100)
        private String sku;

        @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
        private BigDecimal quantity;

        @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
        private BigDecimal unitPrice;

        @Column(name = "discount_pct", nullable = false, precision = 9, scale = 4)
        private BigDecimal discountPct = BigDecimal.ZERO;

        @Column(name = "tax_name", length = 100)
        private String taxName;

        @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
        private BigDecimal taxRate = BigDecimal.ZERO;

        @Column(name = "discount", nullable = false, precision = 19, scale = 4)
        private BigDecimal discount = BigDecimal.ZERO;

        @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal taxAmount = BigDecimal.ZERO;

        @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
        private BigDecimal lineTotal = BigDecimal.ZERO;

        public boolean stocked() {
            return !"SERVICE".equals(itemType);
        }
    }
}
