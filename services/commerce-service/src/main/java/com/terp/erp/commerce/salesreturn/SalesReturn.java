package com.terp.erp.commerce.salesreturn;

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
 * Goods a customer sends back from a posted invoice (ERP manual 6.3): DRAFT → APPROVED → COMPLETED. Completing puts
 * the stocked lines back into the warehouse. The credit note comes with accounting.
 */
@Getter
@Setter
@Entity
@Table(name = "sales_return")
public class SalesReturn extends BaseAuditableEntity {
    public enum EStatus { DRAFT, APPROVED, COMPLETED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "return_number", nullable = false, length = 30)
    private String number;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    /** The receiving warehouse; null for a service-wise invoice. */
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "reason")
    private String reason;

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

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "return_id", nullable = false)
    @OrderBy("position")
    private List<Line> lines = new ArrayList<>();

    /** A returned quantity of one invoice line, priced as on the invoice. */
    @Getter
    @Setter
    @Entity
    @Table(name = "sales_return_line")
    public static class Line {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "position", nullable = false)
        private int position;

        @Column(name = "invoice_line_id", nullable = false)
        private Long invoiceLineId;

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

        @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
        private BigDecimal taxRate = BigDecimal.ZERO;

        @Column(name = "discount", nullable = false, precision = 19, scale = 4)
        private BigDecimal discount = BigDecimal.ZERO;

        @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal taxAmount = BigDecimal.ZERO;

        @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
        private BigDecimal lineTotal = BigDecimal.ZERO;

        @Column(name = "reason")
        private String reason;

        public boolean stocked() {
            return !"SERVICE".equals(itemType);
        }
    }
}
