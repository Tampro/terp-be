package com.terp.erp.iam.saas;

public enum EPaymentMethod {
    BANK_TRANSFER,
    /** Paid through Stripe Checkout; approved by the Stripe webhook (docs/analysis/06 §3.3.2 Payment). */
    STRIPE,
    /** Plan set by a super admin (upgrade from the companies list); recorded as an approved order for history. */
    MANUAL
}
