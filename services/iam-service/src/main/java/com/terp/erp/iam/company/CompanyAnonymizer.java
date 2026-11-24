package com.terp.erp.iam.company;

import com.terp.erp.iam.model.Company;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Retention (PO 2026-11-24): a company deleted 5 years ago is anonymised. Phase 1 = IAM's own personal data, in one
 * transaction:
 * <ul>
 *   <li>company: name → "Anonymized company #id", contact email cleared;</li>
 *   <li>its accounts: username / email / full name replaced by placeholders, mobile and avatar cleared (the Keycloak
 *       accounts were already removed at deletion);</li>
 *   <li>login history: username placeholder, IP, user agent, browser, OS, place and ISP cleared;</li>
 *   <li>helpdesk tickets and replies: title, description, message and author replaced;</li>
 *   <li>company settings (profile, integrations, secrets) and IP rules: deleted.</li>
 * </ul>
 * Then {@value #EVENT} is published (outbox, same transaction) so the other services anonymise their rows; until
 * they subscribe, their data stays as it is. Business records without personal data (orders, invoices, amounts)
 * are kept. Not reversible.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyAnonymizer {
    public static final String EVENT = "iam.company.anonymize";
    static final String ANON = "[anonymized]";

    private final JdbcTemplate jdbc;
    private final OutboxPublisher outbox;
    private final Clock clock;

    @Transactional
    public void anonymize(Company company) {
        long id = company.getId();
        jdbc.update("UPDATE company SET name = ?, email = NULL, anonymized_at = ? WHERE id = ? AND deleted = 1",
                "Anonymized company #" + id, java.sql.Timestamp.from(clock.instant()), id);
        jdbc.update("UPDATE user_account SET username = CONCAT('anon-', id), "
                + "email = CONCAT('anon-', id, '@anonymized.invalid'), full_name = ?, mobile = NULL, "
                + "avatar_media_id = NULL WHERE company_id = ?", "Anonymized user", id);
        jdbc.update("UPDATE login_history SET username = CONCAT('anon-', COALESCE(user_id, 0)), ip_address = NULL, "
                + "user_agent = NULL, browser = NULL, os = NULL, city = NULL, country = NULL, isp = NULL "
                + "WHERE company_id = ?", id);
        jdbc.update("UPDATE helpdesk_ticket SET title = ?, description = ? WHERE company_id = ?", ANON, ANON, id);
        jdbc.update("UPDATE helpdesk_reply SET message = ?, author_name = ? WHERE company_id = ?", ANON, ANON, id);
        jdbc.update("DELETE FROM company_setting WHERE company_id = ?", id);
        jdbc.update("DELETE FROM company_allowed_ip WHERE company_id = ?", id);
        outbox.publish(id, EVENT, "COMPANY", id, Payload.of("companyId", id));
        log.info("Retention: company {} anonymised", id);
    }
}
