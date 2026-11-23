package com.terp.erp.iam.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformWebhookRepository extends JpaRepository<PlatformWebhook, Long> {
    Optional<PlatformWebhook> findByIdAndDeletedFalse(Long id);

    Page<PlatformWebhook> findByDeletedFalse(Pageable pageable);

    Page<PlatformWebhook> findByEventCodeAndDeletedFalse(EPlatformWebhookEvent eventCode, Pageable pageable);

    List<PlatformWebhook> findByEventCodeAndActiveTrueAndDeletedFalseOrderByIdAsc(EPlatformWebhookEvent eventCode);
}
