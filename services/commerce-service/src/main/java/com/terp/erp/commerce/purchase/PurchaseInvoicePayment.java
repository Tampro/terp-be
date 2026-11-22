package com.terp.erp.commerce.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.commerce.common.BaseAuditableEntity;

import java.math.BigDecimal;

/** An accounting payment (paymentId = accounting-service's id) applied to an invoice; once per (invoice, payment). */
@Getter
@Setter
@Entity
@Table(name = "purchase_invoice_payment")
public class PurchaseInvoicePayment extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
