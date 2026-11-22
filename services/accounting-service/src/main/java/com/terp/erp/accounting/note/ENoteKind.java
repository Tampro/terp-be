package com.terp.erp.accounting.note;

/** CREDIT = credit note to a customer (from a sales return); DEBIT = debit note to a vendor (from a purchase return). */
public enum ENoteKind {
    CREDIT("CN"), DEBIT("DN");

    private final String prefix;

    ENoteKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /** Journal source type. */
    public String sourceType() {
        return this == CREDIT ? "CREDIT_NOTE" : "DEBIT_NOTE";
    }
}
