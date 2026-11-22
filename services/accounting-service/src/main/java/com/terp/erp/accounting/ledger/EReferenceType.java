package com.terp.erp.accounting.ledger;

/** What a manual journal entry is for (manual 13 "Reference type"). */
// TODO(assumption): the list is not given by the manual or the TERP Core analysis; confirm with the chief accountant.
public enum EReferenceType {
    GENERAL, ADJUSTMENT, ACCRUAL, DEPRECIATION, CORRECTION, OPENING_BALANCE, REVERSAL, OTHER
}
