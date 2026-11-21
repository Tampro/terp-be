package com.terp.erp.iam.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LandingSectionRepository extends JpaRepository<LandingSection, Long> {
    Optional<LandingSection> findBySectionKey(String sectionKey);
}
