package com.terp.erp.iam.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;

/** A referrer asks to be paid its commission. Approving records the payment (made outside the system). */
@Getter
@Setter
@Entity
@Table(name = "payout_request")
public class PayoutRequest extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EPayoutStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processed_by", length = 64)
    private String processedBy;
}
