package com.terp.erp.iam.saas;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyPlanTrialRepository extends JpaRepository<CompanyPlanTrial, Long> {
    boolean existsByCompanyIdAndPlanId(Long companyId, Long planId);
}
