package com.terp.erp.order.mileage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** Points an order's use took from one lot (given back exactly on cancel / expiry). */
@Getter
@Setter
@Entity
@Table(name = "mileage_use_lot")
@IdClass(MileageUseLot.Key.class)
public class MileageUseLot {
    @Id @Column(name = "order_id") private Long orderId;
    @Id @Column(name = "lot_id") private Long lotId;
    @Column(name = "points", nullable = false) private int points;

    public record Key(Long orderId, Long lotId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
