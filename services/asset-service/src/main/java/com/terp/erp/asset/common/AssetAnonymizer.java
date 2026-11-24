package com.terp.erp.asset.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for asset-service (PO 2026-11-24: 5 years after the company was deleted): who held each asset
 * (employee name, number, email copied on assignments) and the check-out / return notes. Kept: assets, codes,
 * conditions, dates, maintenance and depreciation. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class AssetAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE asset_assignment SET employee_name = '[anonymized]', employee_no = NULL, employee_email = NULL, checkout_notes = NULL, return_notes = NULL WHERE company_id = ?", companyId);
    }
}
