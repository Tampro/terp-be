package com.terp.erp.order.mileage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "mileage_entry")
public class MileageEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false, length = 10) private EMileageEntryType type;
    @Column(name = "points", nullable = false) private int points;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "order_no", length = 30) private String orderNo;
    @Column(name = "lot_id") private Long lotId;
    @Column(name = "reason", length = 200) private String reason;
    @Column(name = "created_by", length = 64) private String createdBy;
    @Column(name = "created_date", nullable = false) private Instant createdDate;
}
