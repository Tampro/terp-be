package com.terp.erp.support.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortalSettingsRepository extends JpaRepository<PortalSettings, Long> {
    Optional<PortalSettings> findByCompanyId(Long companyId);

    Optional<PortalSettings> findBySlug(String slug);
}
