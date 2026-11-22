package com.terp.erp.iam.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyWebhookRepository extends JpaRepository<CompanyWebhook, Long> {
    Optional<CompanyWebhook> findByIdAndDeletedFalse(Long id);

    Page<CompanyWebhook> findByCompanyIdAndDeletedFalse(Long companyId, Pageable pageable);

    List<CompanyWebhook> findByCompanyIdAndEventCodeAndActiveTrueAndDeletedFalseOrderByIdAsc(Long companyId,
                                                                                         EWebhookEvent eventCode);

    Page<CompanyWebhook> findByCompanyIdAndEventCodeAndDeletedFalse(Long companyId, EWebhookEvent eventCode,
                                                                    Pageable pageable);
}
