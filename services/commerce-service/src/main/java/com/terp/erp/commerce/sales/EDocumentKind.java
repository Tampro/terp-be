package com.terp.erp.commerce.sales;

/** The three sales documents sharing one shape; the number prefix comes with the kind. */
public enum EDocumentKind {
    QUOTATION("QT"), PROPOSAL("SP"), INVOICE("SI");

    private final String prefix;

    EDocumentKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
