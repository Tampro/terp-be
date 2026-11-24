package com.terp.erp.employee.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for employee-service (PO 2026-11-24: 5 years after the company was deleted, then anonymised): identity,
 * contact, address, emergency contact, bank and tax data of every employee and trainer, and the free text of the
 * cases that describe people (complaints, warnings, trips). Kept: codes, dates, statuses, org structure and amounts
 * (no personal data on their own). Files (profile photo, documents) are media-service's part. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class EmployeeAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE employee SET full_name = ?, email = CONCAT('anon-', id, '@anonymized.invalid'), "
                + "phone = NULL, date_of_birth = NULL, gender = NULL, profile_media_id = NULL, address_line1 = NULL, "
                + "address_line2 = NULL, city = NULL, state = NULL, country = NULL, postal_code = NULL, "
                + "emergency_name = NULL, emergency_relationship = NULL, emergency_phone = NULL, bank_name = NULL, "
                + "account_holder_name = NULL, account_number_enc = NULL, bank_identifier_code = NULL, "
                + "bank_branch = NULL, tax_payer_id_enc = NULL WHERE company_id = ?", "Anonymized employee", companyId);
        jdbc.update("UPDATE trainer SET name = ?, contact_phone = NULL, email = NULL, expertise = NULL, "
                + "qualification = NULL WHERE company_id = ?", "Anonymized trainer", companyId);
        jdbc.update("UPDATE employee_complaint SET subject = ?, description = NULL, investigation_notes = NULL, "
                + "resolution_action = NULL, follow_up_action = NULL, feedback = NULL, document_media_id = NULL "
                + "WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE employee_warning SET subject = ?, description = NULL, employee_response = NULL, "
                + "improvement_plan_goals = NULL, improvement_plan_progress = NULL, document_media_id = NULL "
                + "WHERE company_id = ?", ANON, companyId);
        jdbc.update("UPDATE employee_trip SET purpose = ?, destination = ?, description = NULL, "
                + "expected_outcomes = NULL, document_media_id = NULL WHERE company_id = ?", ANON, ANON, companyId);
    }
}
