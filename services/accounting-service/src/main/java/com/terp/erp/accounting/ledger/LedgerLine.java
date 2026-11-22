package com.terp.erp.accounting.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One counted (POSTED or REVERSED) journal line with its entry header, read for the ledger reports. */
public record LedgerLine(Long entryId, String number, LocalDate date, String reference, String description,
                         String sourceType, Long sourceId, Long accountId, String lineDescription, BigDecimal debit,
                         BigDecimal credit) {
}
