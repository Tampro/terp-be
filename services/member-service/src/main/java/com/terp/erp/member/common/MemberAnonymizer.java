package com.terp.erp.member.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for member-service (company anonymised by IAM, iam.company.anonymize): every delivery address of the
 * company is deleted and each membership's customer-realm subject becomes {@code anon-<id>}, so no row can be tied to a
 * person any more. Kept as evidence: consent versions and times, join / leave dates. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class MemberAnonymizer implements CompanyAnonymizeHandler {
    static final String DELETE_ADDRESSES = "DELETE FROM customer_address WHERE company_id = ?";
    static final String ANONYMIZE_SUBJECTS =
            "UPDATE customer SET keycloak_sub = CONCAT('anon-', id) WHERE company_id = ? AND keycloak_sub NOT LIKE 'anon-%'";

    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update(DELETE_ADDRESSES, companyId);
        jdbc.update(ANONYMIZE_SUBJECTS, companyId);
    }
}
