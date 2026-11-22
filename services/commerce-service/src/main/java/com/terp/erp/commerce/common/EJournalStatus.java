package com.terp.erp.commerce.common;

/**
 * The accounting journal of a posted invoice / completed return. It is written by accounting-service from the
 * outbox event commerce.journal.requested, so it lags the posting by a few seconds: PENDING until accounting answers
 * with accounting.journal.posted (POSTED) or accounting.journal.failed (FAILED, with its error code).
 */
public enum EJournalStatus {
    PENDING, POSTED, FAILED
}
