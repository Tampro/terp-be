package com.terp.erp.iam.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateContentRepository extends JpaRepository<EmailTemplateContent, Long> {
    List<EmailTemplateContent> findByTemplateIdOrderByLang(Long templateId);

    Optional<EmailTemplateContent> findByTemplateIdAndLang(Long templateId, String lang);
}
