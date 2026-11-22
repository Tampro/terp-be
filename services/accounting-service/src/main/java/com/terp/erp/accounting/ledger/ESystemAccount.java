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
    COGS("632"),
    /** Year-end close: revenue and expense balances are carried here, then its net to RETAINED_EARNINGS. */
    INCOME_SUMMARY("911"),
    RETAINED_EARNINGS("4212"),
    /**
     * Payroll payments debit it (Dr 334 / Cr bank). Added after companies were seeded: AccountMapping maps it lazily
     * to the company's 334 account the first time it is missing.
     */
    SALARY_PAYABLE("334");

    /** Account code the default chart maps this key to. */
    private final String defaultCode;

    ESystemAccount(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String defaultCode() {
        return defaultCode;
    }
}
