package com.terp.erp.accounting.ledger;

/**
 * DRAFT = manual entry not yet posted (moves no balance); POSTED = in the books; REVERSED = posted, then cancelled
 * by a reversal entry (both still count in reports and cancel each other).
 */
public enum EJournalStatus {
    DRAFT, POSTED, REVERSED
}
