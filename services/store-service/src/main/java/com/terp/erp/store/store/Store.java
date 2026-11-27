package com.terp.erp.store.store;

import com.terp.erp.store.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/** A store of the chain: one warehouse, one daily order window in its own time zone, its delivery fees. */
@Getter
@Setter
@Entity
@Table(name = "store")
public class Store extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lon", precision = 9, scale = 6)
    private BigDecimal lon;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "time_zone", nullable = false, length = 40)
    private String timeZone = "Asia/Seoul";

    @Column(name = "order_start", nullable = false)
    private LocalTime orderStart;

    @Column(name = "order_end", nullable = false)
    private LocalTime orderEnd;

    @Column(name = "delivery_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "free_delivery_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal freeDeliveryThreshold = BigDecimal.ZERO;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "first_order_min_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal firstOrderMinAmount = BigDecimal.ZERO;

    @Column(name = "bag_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal bagFee = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** 0 while live, the row id once deleted — frees uk_store_code / uk_store_warehouse for a new store. */
    @Column(name = "deleted_key", nullable = false)
    private long deletedKey;
}
