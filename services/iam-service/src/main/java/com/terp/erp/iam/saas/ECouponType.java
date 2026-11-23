package com.terp.erp.iam.saas;

/**
 * PERCENTAGE: the price minus value %. FLAT: the price minus value (never below zero). FIXED: the order costs
 * exactly value, so the discount is price - value; a fixed price at or above the plan price gives no discount
 * (decided by the PO for ARQ C-07, 2026-11-23; TERP Core's "fixed" was a second FLAT).
 */
public enum ECouponType {
    PERCENTAGE, FLAT, FIXED
}
