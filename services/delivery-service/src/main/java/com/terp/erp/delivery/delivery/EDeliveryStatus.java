package com.terp.erp.delivery.delivery;

/** READY (order paid) → ASSIGNED → PICKED_UP → DELIVERED; Back Office cancels from READY / ASSIGNED. */
public enum EDeliveryStatus {
    READY, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED
}
