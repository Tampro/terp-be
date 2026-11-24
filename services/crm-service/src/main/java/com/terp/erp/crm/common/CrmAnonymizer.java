package com.terp.erp.crm.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for crm-service (PO 2026-11-24: 5 years after the company was deleted, then anonymised): leads and deals
 * (names, emails, phones, notes), the free text logged on them (calls, entries, tasks, links), and contracts
 * (customer and owner names, descriptions, comments, notes, renewal notes, attachment file names, signatures: signer
 * name, email, IP and the signature image; the content hash stays as proof the document existed). Kept: stages,
 * amounts, dates, statuses, numbers. Attached files themselves are media-service's part. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class CrmAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";
    /** 1x1 transparent PNG: signature_png is NOT NULL. */
    private static final byte[] BLANK_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE crm_lead SET name = ?, email = CONCAT('anon-', id, '@anonymized.invalid'), subject = ?, "
                + "phone = NULL, notes = NULL WHERE company_id = ?", ANON, ANON, companyId);
        jdbc.update("UPDATE crm_deal SET name = ?, phone = NULL, notes = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE crm_call SET subject = ?, description = NULL WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE crm_entry SET title = NULL, body = NULL, email_to = NULL, author_name = NULL "
                + "WHERE company_id = ?", companyId);
        jdbc.update("UPDATE crm_task SET name = ? WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE crm_link SET ref_name = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE contract SET subject = ?, customer_name = ?, description = NULL, owner_name = NULL "
                + "WHERE company_id = ?", ANON, ANON, companyId);
        jdbc.update("UPDATE contract_comment SET message = ?, author_name = NULL WHERE company_id = ?", ANON,
                companyId);
        jdbc.update("UPDATE contract_note SET note = ? WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE contract_renewal SET notes = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE contract_attachment SET file_name = ? WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE contract_signature SET signer_name = ?, signer_email = NULL, ip = NULL, signature_png = ? "
                + "WHERE company_id = ?", ANON, BLANK_PNG, companyId);
    }
}
