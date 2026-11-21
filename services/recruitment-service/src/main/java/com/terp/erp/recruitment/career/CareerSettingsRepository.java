package com.terp.erp.recruitment.career;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CareerSettingsRepository extends JpaRepository<CareerSettings, Long> {
    Optional<CareerSettings> findByCompanyId(Long companyId);

    Optional<CareerSettings> findBySlug(String slug);
}
