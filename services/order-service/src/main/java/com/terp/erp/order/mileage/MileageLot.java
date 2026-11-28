package com.terp.erp.order.mileage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "mileage_lot")
public class MileageLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "customer_id", nullable = false) private Long customerId;
    /** EARN or ADJUST. */
    @Column(name = "source", nullable = false, length = 10) private String source;
    @Column(name = "points", nullable = false) private int points;
    @Column(name = "remaining", nullable = false) private int remaining;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "earn_order_id") private Long earnOrderId;
    @Column(name = "created_date", nullable = false) private Instant createdDate;
}
