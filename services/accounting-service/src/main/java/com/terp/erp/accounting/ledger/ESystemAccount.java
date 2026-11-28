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
    SALARY_PAYABLE("334"),
    /**
     * Commerce postings (sales invoice / return, purchase, payroll accrual), added after companies were seeded like
     * SALARY_PAYABLE: mapped lazily, and backfilled by changeset ERP-ACCOUNTING:20261122700000.
     * TODO(assumption): 5113 for service lines, 5213 for sales returns, 6421 for salaries - chief accountant to confirm.
     */
    SERVICE_REVENUE("5113"),
    SALES_RETURNS("5213"),
    SALARY_EXPENSE("6421"),
    /**
     * Express orders paid through the PG (SHOP_ORDER postings), until the PG pays out. Mapped lazily like SALARY_PAYABLE.
     * TODO(assumption): 138 (the chart has no 1388 sub-account) — chief accountant to confirm.
     */
    PG_CLEARING("138");

    /** Account code the default chart maps this key to. */
    private final String defaultCode;

    ESystemAccount(String defaultCode) {
        this.defaultCode = defaultCode;
    }

    public String defaultCode() {
        return defaultCode;
    }
}
