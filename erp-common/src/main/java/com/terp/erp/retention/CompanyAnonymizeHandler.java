package com.terp.erp.retention;

/**
 * Anonymises one service's personal data of a company IAM has anonymised (retention, PO 2026-11-24: 5 years after
 * the deletion). Must be idempotent: the event can be delivered more than once. Runs in the consumer's transaction.
 */
public interface CompanyAnonymizeHandler {
    void anonymize(long companyId);
}
