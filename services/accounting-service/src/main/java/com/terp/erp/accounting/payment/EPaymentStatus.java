package com.terp.erp.accounting.payment;

/** PENDING → clear → CLEARED | cancel → CANCELLED; CLEARED and CANCELLED are view only. */
public enum EPaymentStatus {
    PENDING, CLEARED, CANCELLED
}
