package com.terp.erp.accounting.bank;

/** Postings in this service write CLEARED; RECONCILED once matched to the bank statement. */
public enum ETransactionStatus {
    PENDING, CLEARED, RECONCILED
}
