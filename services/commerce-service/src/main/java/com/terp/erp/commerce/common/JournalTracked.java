package com.terp.erp.commerce.common;

/** A commerce document whose accounting journal is written asynchronously (invoices and returns). */
public interface JournalTracked {
    Long getCompanyId();

    EJournalStatus getJournalStatus();

    void setJournalStatus(EJournalStatus status);

    void setJournalErrorCode(String code);
}
