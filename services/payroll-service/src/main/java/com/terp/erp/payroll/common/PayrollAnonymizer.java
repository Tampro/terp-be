package com.terp.erp.payroll.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finance retention for payroll-service: runs on iam.company.anonymize-finance, sent 10 years after the company
 * was deleted (payroll runs and payslips are accounting vouchers, Law on Accounting 2015 Art. 41; PO 2026-11-24).
 * Employee names, numbers and emails copied on salaries, entries and payslips, and loan / overtime / salary
 * notes are replaced. Amounts, components, periods and statuses stay. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class PayrollAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE employee_salary SET employee_name = '[anonymized]', employee_no = NULL, employee_email = NULL, notes = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE payroll_entry SET employee_name = '[anonymized]', employee_no = NULL, employee_email = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE payslip SET employee_name = '[anonymized]', employee_no = NULL, employee_email = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE employee_loan SET reason = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE employee_overtime SET notes = NULL WHERE company_id = ?", companyId);
    }
}
