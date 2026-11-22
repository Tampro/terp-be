package com.terp.erp.accounting.ledger;

/**
 * Roles an account plays in automatic postings. Each company maps every key to one of its own accounts; the
 * defaults are VAS Circular 200 codes.
 */
public enum ESystemAccount {
    BANK("112"),
    CASH("111"),
    RECEIVABLE("131"),
    PAYABLE("331"),
    OUTPUT_VAT("33311"),
    INPUT_VAT("1331"),
    SALES_REVENUE("5111"),
    OTHER_REVENUE("711"),
    GENERAL_EXPENSE("6428"),
    BANK_CHARGES("6427"),
    INVENTORY("156"),
    COGS("632");

    /** Account code the default chart maps this key to. */
    private final String defaultCode;

    ESystemAccount(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String defaultCode() {
        return defaultCode;
    }
}
