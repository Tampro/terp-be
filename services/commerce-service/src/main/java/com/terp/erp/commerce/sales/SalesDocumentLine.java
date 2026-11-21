package com.terp.erp.commerce.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One line; item name, sku, type and tax are a copy taken when the line was saved. */
@Getter
@Setter
@Entity
@Table(name = "sales_document_line")
public class SalesDocumentLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** PRODUCT, SERVICE or PART, as in inventory-service. */
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
