package com.terp.erp.employee.certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LetterTemplateRepository extends JpaRepository<LetterTemplate, Long> {
    Optional<LetterTemplate> findByCompanyIdAndLetterTypeAndLang(Long companyId, ELetterType letterType, String lang);
}
