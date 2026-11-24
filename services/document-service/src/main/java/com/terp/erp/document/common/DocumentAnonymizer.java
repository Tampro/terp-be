package com.terp.erp.document.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for document-service (PO 2026-11-24: 5 years after the company was deleted): employee names, numbers
 * and emails copied on contracts and acknowledgments, contract terms, notes and decision comments. Kept: contract
 * numbers, dates, statuses, templates (the company's own text). Files are media-service's part. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class DocumentAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE employee_contract SET employee_name = '[anonymized]', employee_no = NULL, employee_email = NULL, terms_and_conditions = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE document_acknowledgment SET employee_name = '[anonymized]', employee_email = NULL, note = NULL, assigned_by_name = NULL, acknowledge_note = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE hr_document SET decided_by_name = NULL, decision_comment = NULL WHERE company_id = ?", companyId);
    }
}
