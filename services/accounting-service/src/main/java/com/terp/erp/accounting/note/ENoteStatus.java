package com.terp.erp.accounting.note;

/** DRAFT → approve → APPROVED → (applied in cleared payments) PARTIAL → APPLIED at balance 0. */
public enum ENoteStatus {
    DRAFT, APPROVED, PARTIAL, APPLIED
}
