package com.terp.erp.accounting.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finance retention for accounting-service: runs on iam.company.anonymize-finance, sent 10 years after the company
 * was deleted (accounting vouchers and books are kept 10 years, Law on Accounting 2015 Art. 41; PO 2026-11-24).
 * Customers and vendors lose their names, contacts, tax number and addresses; party names and notes on payments and
 * credit/debit notes, and the company's bank account holder and numbers, are replaced. Amounts, accounts, journals,
 * numbers and dates stay. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class AccountingAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";
    private static final String PARTY = "UPDATE %s SET company_name = ?, contact_person = ?, "
            + "email = CONCAT('anon-', id, '@anonymized.invalid'), mobile = NULL, tax_number = NULL, "
            + "billing_name = NULL, billing_address = NULL, billing_address2 = NULL, billing_city = NULL, "
            + "billing_state = NULL, billing_country = NULL, billing_zip = NULL, shipping_name = NULL, "
            + "shipping_address = NULL, shipping_address2 = NULL, shipping_city = NULL, shipping_state = NULL, "
            + "shipping_country = NULL, shipping_zip = NULL, notes = NULL WHERE company_id = ?";

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update(PARTY.formatted("customer"), ANON, ANON, companyId);
        jdbc.update(PARTY.formatted("vendor"), ANON, ANON, companyId);
        jdbc.update("UPDATE payment SET party_name = ?, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE account_note SET party_name = ?, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE bank_account SET holder_name = ?, account_number = ?, iban = NULL, swift = NULL "
                + "WHERE company_id = ?", ANON, ANON, companyId);
    }
}
