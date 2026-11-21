package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.saas.EBillingCycle;
import com.terp.erp.iam.saas.EOrderStatus;
import com.terp.erp.iam.saas.EPaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A company's purchase of a plan (docs/analysis/01 §5, table `plan_orders`). Prices are frozen at order time;
 * approval by a super admin activates or extends the plan.
 */
@Getter
@Setter
@Entity
@Table(name = "plan_order")
public class PlanOrder extends BaseAuditableEntity {
    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    /** Proof of a bank transfer the company attached (docs/analysis/06 §10.2.3 Receipt): a media file id. */
    @Column(name = "receipt_file_id")
    private Long receiptFileId;

    /** Stripe Checkout Session of a STRIPE order: the webhook finds the order by it. */
    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private EBillingCycle billingCycle;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "original_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private EPaymentMethod paymentMethod;

    /** Bank transfer reference given by the company (free text, shown to the approver). */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EOrderStatus status;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** Keycloak subject of the super admin who approved or rejected. */
    @Column(name = "processed_by", length = 64)
    private String processedBy;

    @Column(name = "notes", length = 500)
    private String notes;
}
