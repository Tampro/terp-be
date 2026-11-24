package com.terp.erp.notification.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for notification-service (PO 2026-11-24: 5 years after the company was deleted, then anonymised): the
 * delivery log loses its targets (email addresses, phone numbers, webhook URLs) and recipient references, the stored
 * event envelopes (they carry names and titles) are emptied, and in-app notification titles are replaced. Kept: event
 * ids and types, channels, statuses, dates — enough for the dedupe key and delivery statistics. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class NotificationAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE delivery SET target = NULL, recipient_ref = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE processed_event SET envelope = '{}' WHERE company_id = ?", companyId);
        jdbc.update("UPDATE user_notification SET title = ? WHERE company_id = ?", "[anonymized]", companyId);
    }
}
