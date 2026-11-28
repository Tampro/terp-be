package com.terp.erp.order.coupon;

import com.terp.erp.order.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "coupon")
public class Coupon extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "code", nullable = false, length = 30) private String code;
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false, length = 10) private ECouponType type;
    @Column(name = "value", nullable = false, precision = 19, scale = 4) private BigDecimal value;
    @Column(name = "max_discount", precision = 19, scale = 4) private BigDecimal maxDiscount;
    @Column(name = "min_subtotal", precision = 19, scale = 4) private BigDecimal minSubtotal;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "total_limit") private Integer totalLimit;
    @Column(name = "per_customer_limit") private Integer perCustomerLimit;
    @Column(name = "used_count", nullable = false) private int usedCount;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "deleted", nullable = false) private boolean deleted;
    @Column(name = "deleted_key", nullable = false) private long deletedKey;
}
