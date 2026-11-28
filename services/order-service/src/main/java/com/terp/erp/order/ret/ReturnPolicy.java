package com.terp.erp.order.ret;

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
@Table(name = "return_policy")
@EntityListeners(AuditingEntityListener.class)
public class ReturnPolicy {
    /** FR-MYO-008: Magic Now pick-up fee for a change of mind, deducted from the refund (incl. VAT). */
    public static final BigDecimal DEFAULT_PICKUP_FEE = new BigDecimal("3000");
    /** TODO(assumption): one window for every item; fresh food (7 days) once items carry a flag. */
    public static final int WINDOW_DAYS = 30;

    @Id @Column(name = "company_id") private Long companyId;
    @Column(name = "pickup_fee", nullable = false, precision = 19, scale = 4) private BigDecimal pickupFee;
    @CreatedBy @Column(name = "created_by", updatable = false, length = 64) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by", length = 64) private String updatedBy;
    @CreatedDate @Column(name = "created_date", updatable = false) private Instant createdDate;
    @LastModifiedDate @Column(name = "updated_date") private Instant updatedDate;

    public static ReturnPolicy defaults(Long companyId) {
        ReturnPolicy p = new ReturnPolicy();
        p.setCompanyId(companyId);
        p.setPickupFee(DEFAULT_PICKUP_FEE);
        return p;
    }
}
