package com.terp.erp.accounting.payment;

/** CUSTOMER = customer payment (CP-, money in), VENDOR = vendor payment (VP-, money out). */
public enum EPaymentKind {
    CUSTOMER("CP"), VENDOR("VP");

    private final String prefix;

    EPaymentKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /** Bank transaction and journal source type. */
    public String sourceType() {
        return this == CUSTOMER ? "CUSTOMER_PAYMENT" : "VENDOR_PAYMENT";
    }
}
