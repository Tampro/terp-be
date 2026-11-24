package com.terp.erp.attendance.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for attendance-service (PO 2026-11-24: 5 years after the company was deleted): free text people wrote
 * about themselves or others (leave and regularization reasons, manager comments, time entry descriptions, record
 * notes) and the biometric ids. Kept: dates, hours, statuses, balances. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class AttendanceAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE leave_application SET reason = '[anonymized]', manager_comments = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE attendance_regularization SET reason = '[anonymized]', manager_comments = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE time_entry SET description = '[anonymized]', project = NULL, manager_comments = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE attendance_record SET notes = NULL, biometric_id = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE leave_balance SET adjustment_reason = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE attendance_setting SET allowed_ips = NULL WHERE company_id = ?", companyId);
    }
}
