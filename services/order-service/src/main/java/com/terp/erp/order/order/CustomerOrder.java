package com.terp.erp.order.order;

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

/** A customer's order at one store; money and address are a snapshot taken at checkout. phoneEnc = FieldCipher output. */
@Getter
@Setter
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "warehouse_id", nullable = false) private Long warehouseId;
    @Column(name = "order_no", nullable = false, length = 20) private String orderNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private EOrderStatus status;
    @Column(name = "failure_reason", length = 20) private String failureReason;
    @Enumerated(EnumType.STRING) @Column(name = "substitution", nullable = false, length = 5) private ESubstitution substitution;
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 4) private BigDecimal subtotal;
    @Column(name = "delivery_fee", nullable = false, precision = 19, scale = 4) private BigDecimal deliveryFee;
    @Column(name = "bag_fee", nullable = false, precision = 19, scale = 4) private BigDecimal bagFee;
    @Column(name = "total", nullable = false, precision = 19, scale = 4) private BigDecimal total;
    @Column(name = "store_name", nullable = false, length = 255) private String storeName;
    @Column(name = "recipient_name", nullable = false, length = 50) private String recipientName;
    @Column(name = "phone_enc", nullable = false, length = 255) private String phoneEnc;
    @Column(name = "postcode", nullable = false, length = 5) private String postcode;
    @Column(name = "address1", nullable = false, length = 200) private String address1;
    @Column(name = "address2", length = 200) private String address2;
    @Column(name = "lat", nullable = false, precision = 9, scale = 6) private BigDecimal lat;
    @Column(name = "lon", nullable = false, precision = 9, scale = 6) private BigDecimal lon;
    @Column(name = "delivery_note", length = 200) private String deliveryNote;
    @Column(name = "note", length = 200) private String note;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
}
