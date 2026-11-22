package com.terp.erp.accounting.ledger;

/** Only POSTED is written today; DRAFT and REVERSED are reserved for manual entries (§13). */
public enum EJournalStatus {
    DRAFT, POSTED, REVERSED
}
