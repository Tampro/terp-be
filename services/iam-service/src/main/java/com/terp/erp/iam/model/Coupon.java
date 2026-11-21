package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.saas.ECouponType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Discount code for plan orders (docs/analysis/01 §5, table `coupons`). Limits count PENDING + APPROVED orders. */
@Getter
@Setter
@Entity
@Table(name = "coupon")
public class Coupon extends BaseAuditableEntity {
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Optional note shown on the coupon card. */
    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ECouponType type;

    /** Percent (0–100) for PERCENTAGE, money for FLAT. */
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "minimum_spend", precision = 15, scale = 2)
    private BigDecimal minimumSpend;

    /** Orders above this price cannot use the coupon; null = no upper bound. */
    @Column(name = "maximum_spend", precision = 15, scale = 2)
    private BigDecimal maximumSpend;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", nullable = false, length = 10)
    private com.terp.erp.iam.saas.ECouponCodeType codeType;

    @Column(name = "use_limit_total")
    private Integer useLimitTotal;

    @Column(name = "use_limit_per_company")
    private Integer useLimitPerCompany;

    /** Last day the code can be used (inclusive, UTC). */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
