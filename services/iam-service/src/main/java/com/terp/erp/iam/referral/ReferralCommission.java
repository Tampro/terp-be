package com.terp.erp.iam.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.math.BigDecimal;

/** Commission earned by a referrer from one approved bank-transfer order of a company it referred. */
@Getter
@Setter
@Entity
@Table(name = "referral_commission")
public class ReferralCommission extends BaseAuditableEntity {
    @Column(name = "referrer_company_id", nullable = false)
    private Long referrerCompanyId;

    @Column(name = "referred_company_id", nullable = false)
    private Long referredCompanyId;

    /** Unique: an order earns at most one commission even if approval were replayed. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderAmount;

    /** Frozen at approval: later setting changes do not rewrite earned commission. */
    @Column(name = "commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
