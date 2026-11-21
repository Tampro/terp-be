package com.terp.erp.commerce.sales;

/**
 * Quotation / proposal: DRAFT → SENT → ACCEPTED | REJECTED. Invoice: DRAFT → POSTED → PARTIAL → PAID (payments come
 * with accounting). Overdue (invoice) and expired (proposal) are read from the due date, not stored.
 */
public enum EDocumentStatus {
    DRAFT, SENT, ACCEPTED, REJECTED, POSTED, PARTIAL, PAID
}
