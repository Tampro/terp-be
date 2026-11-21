package com.terp.erp.iam.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long> {
    Optional<CompanySetting> findByCompanyIdAndSection(Long companyId, String section);
}
