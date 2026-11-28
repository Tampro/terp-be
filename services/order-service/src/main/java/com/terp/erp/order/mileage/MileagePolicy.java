package com.terp.erp.order.mileage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "mileage_policy")
@EntityListeners(AuditingEntityListener.class)
public class MileagePolicy {
    // TODO(assumption): defaults until the business sets its own — on, 1 %, 12 months, 1,000 points minimum.
    public static final BigDecimal DEFAULT_RATE = BigDecimal.ONE;
    public static final int DEFAULT_EXPIRY_MONTHS = 12;
    public static final int DEFAULT_MIN_REDEEM = 1000;

    @Id @Column(name = "company_id") private Long companyId;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "earn_rate", nullable = false, precision = 5, scale = 2) private BigDecimal earnRate;
    @Column(name = "expiry_months", nullable = false) private int expiryMonths;
    @Column(name = "min_redeem", nullable = false) private int minRedeem;
    @CreatedBy @Column(name = "created_by", updatable = false, length = 64) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by", length = 64) private String updatedBy;
    @CreatedDate @Column(name = "created_date", updatable = false) private Instant createdDate;
    @LastModifiedDate @Column(name = "updated_date") private Instant updatedDate;

    public static MileagePolicy defaults(Long companyId) {
        MileagePolicy p = new MileagePolicy();
        p.setCompanyId(companyId);
        p.setActive(true);
        p.setEarnRate(DEFAULT_RATE);
        p.setExpiryMonths(DEFAULT_EXPIRY_MONTHS);
        p.setMinRedeem(DEFAULT_MIN_REDEEM);
        return p;
    }
}
