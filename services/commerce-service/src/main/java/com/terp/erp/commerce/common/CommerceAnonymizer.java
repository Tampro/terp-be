package com.terp.erp.commerce.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finance retention for commerce-service: runs on iam.company.anonymize-finance, sent 10 years after the company
 * was deleted (invoices are accounting vouchers, kept 10 years, Law on Accounting 2015 Art. 41; PO 2026-11-24).
 * The customer / vendor name copied on each document, the customer email and the notes are replaced. Items,
 * quantities, prices, taxes, numbers and dates stay. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class CommerceAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE sales_document SET customer_name = ?, customer_email = NULL, notes = NULL "
                + "WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE sales_return SET customer_name = ?, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE purchase_invoice SET vendor_name = ?, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE purchase_return SET vendor_name = ?, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE pos_order SET customer_name = ? WHERE company_id = ?", ANON, companyId);
    }
}
