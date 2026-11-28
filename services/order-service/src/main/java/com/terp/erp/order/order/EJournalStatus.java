package com.terp.erp.order.order;

/** Journal of a paid order in accounting: PENDING until accounting answers (outbox, a few seconds). */
public enum EJournalStatus {
    PENDING, POSTED, FAILED
}
