package com.terp.erp.delivery.delivery;

import com.terp.erp.delivery.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One delivery per paid order. The address and phone are not copied: they are read from order-service on demand. */
@Getter
@Setter
@Entity
@Table(name = "delivery")
public class Delivery extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "order_no", nullable = false, length = 30) private String orderNo;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16) private EDeliveryStatus status;
    @Column(name = "rider_sub", length = 64) private String riderSub;
    @Column(name = "rider_name", length = 120) private String riderName;
    @Column(name = "assigned_at") private Instant assignedAt;
    @Column(name = "picked_up_at") private Instant pickedUpAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancel_reason", length = 200) private String cancelReason;
    @Version @Column(name = "version", nullable = false) private int version;
}
