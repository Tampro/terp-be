package com.terp.erp.support.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for support-service (PO 2026-11-24: 5 years after the company was deleted, then anonymised): who opened
 * each ticket (name, email), its subject, description, note, attachment list and custom field answers; replies
 * (sender name, text, attachments); contact-form messages; messenger messages (text and attachment name). Kept: ticket
 * numbers, statuses, dates, categories and the company's own published content (KB, FAQ, portal pages). Attached
 * files themselves are media-service's part. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class SupportAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE ticket SET name = ?, email = CONCAT('anon-', id, '@anonymized.invalid'), subject = ?, "
                + "description = NULL, attachments = NULL, note = NULL WHERE company_id = ?", ANON, ANON, companyId);
        jdbc.update("UPDATE ticket_reply r JOIN ticket t ON t.id = r.ticket_id SET r.sender_name = NULL, "
                + "r.description = ?, r.attachments = NULL WHERE t.company_id = ?", ANON, companyId);
        jdbc.update("UPDATE ticket_field_value v JOIN ticket t ON t.id = v.ticket_id SET v.field_value = ? "
                + "WHERE t.company_id = ?", ANON, companyId);
        jdbc.update("UPDATE contact_message SET name = ?, email = CONCAT('anon-', id, '@anonymized.invalid'), "
                + "subject = ?, message = ? WHERE company_id = ?", ANON, ANON, ANON, companyId);
        jdbc.update("UPDATE chat_message SET body = NULL, attachment_file_id = NULL, attachment_name = NULL "
                + "WHERE company_id = ?", companyId);
    }
}
