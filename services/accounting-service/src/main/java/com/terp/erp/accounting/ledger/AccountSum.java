package com.terp.erp.accounting.ledger;

import java.math.BigDecimal;

/** Debit and credit totals of one account over counted lines. */
public record AccountSum(Long accountId, BigDecimal debit, BigDecimal credit) {
}
