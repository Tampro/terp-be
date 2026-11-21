package com.terp.erp.iam.referral;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** The single settings row of the referral program (id = 1). Amounts are in the default currency. */
@Getter
@Setter
@Entity
@Table(name = "referral_setting")
public class ReferralSetting {
    public static final long ID = 1L;

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    /** Minimum amount a payout request may ask for. */
    @Column(name = "threshold_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal thresholdAmount;

    @Column(name = "guidelines", columnDefinition = "TEXT")
    private String guidelines;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_date")
    private Instant updatedDate;
}
