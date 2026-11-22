package com.terp.erp.commerce.pos;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.commerce.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A paid POS sale (ERP manual 10): written only once stock has left the warehouse and the income is in the bank
 * account, so there is no status. customerId null = walk-in. customerId, warehouseId and bankAccountId point to
 * accounting- and inventory-service; their names are copied for lists, receipts and reports.
 */
@Getter
@Setter
@Entity
@Table(name = "pos_order")
public class PosOrder extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "pos_number", nullable = false, length = 30)
    private String number;

    @Column(name = "receipt_number", nullable = false, length = 30)
    private String receiptNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "warehouse_name", nullable = false)
    private String warehouseName;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "bank_account_name", nullable = false)
    private String bankAccountName;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;

    /**
     * Cost of the stocked lines at checkout (item purchase price x quantity), sent to accounting for Dr 632 / Cr 156;
     * not stored on the order.
     */
    @Transient
    private BigDecimal costTotal = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("position")
    private List<Line> lines = new ArrayList<>();

    /** One cart line; item name, sku, type, price and tax are a copy taken at the sale. */
    @Getter
    @Setter
    @Entity
    @Table(name = "pos_order_line")
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

        @Column(name = "tax_name", length = 100)
        private String taxName;

        @Column(name = "tax_rate", nullable = false, precision = 9, scale = 4)
        private BigDecimal taxRate = BigDecimal.ZERO;

        @Column(name = "amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal amount = BigDecimal.ZERO;

        @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
        private BigDecimal taxAmount = BigDecimal.ZERO;

        public boolean stocked() {
            return !"SERVICE".equals(itemType);
        }
    }
}
