package com.terp.erp.order.ret;

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
@Table(name = "order_return")
public class OrderReturn extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "order_no", nullable = false, length = 30) private String orderNo;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "return_no", nullable = false, length = 30) private String returnNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16) private EReturnStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "reason", nullable = false, length = 20) private EReturnReason reason;
    @Column(name = "note", length = 500) private String note;
    @Column(name = "reject_note", length = 200) private String rejectNote;
    @Column(name = "goods_amount", nullable = false, precision = 19, scale = 4) private BigDecimal goodsAmount;
    @Column(name = "refund_share", nullable = false, precision = 19, scale = 4) private BigDecimal refundShare;
    @Column(name = "pickup_fee", nullable = false, precision = 19, scale = 4) private BigDecimal pickupFee;
    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 4) private BigDecimal refundAmount;
    @Column(name = "cost_amount", nullable = false, precision = 19, scale = 4) private BigDecimal costAmount;
    @Column(name = "points_back", nullable = false) private int pointsBack;
    @Column(name = "earned_back", nullable = false) private int earnedBack;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "completed_at") private Instant completedAt;
}
