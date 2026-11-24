package com.terp.erp.media.service;

import com.terp.erp.media.constant.EStorageDriver;
import com.terp.erp.media.storage.StorageRouter;
import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Retention for media-service: runs on iam.company.anonymize-finance, 10 years after the company was deleted (some
 * files are attached to accounting vouchers and media cannot tell which; PO 2026-11-24). Every file of the company is
 * deleted for good — rows and folders now, the stored bytes (local volume or S3) after the commit, so a rolled-back
 * transaction never leaves rows pointing at missing bytes. Platform files (company_id NULL) are never touched.
 * Idempotent: a second run finds no rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;
    private final StorageRouter storage;

    private record Stored(String driver, String key) {
    }

    @Override
    public void anonymize(long companyId) {
        List<Stored> stored = jdbc.query("SELECT storage_driver, storage_key FROM media_file WHERE company_id = ?",
                (rs, i) -> new Stored(rs.getString(1), rs.getString(2)), companyId);
        jdbc.update("DELETE FROM media_file WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM media_folder WHERE company_id = ?", companyId);
        if (stored.isEmpty()) {
            return;
        }
        Runnable deleteBytes = () -> {
            int failed = 0;
            for (Stored s : stored) {
                try {
                    storage.of(EStorageDriver.valueOf(s.driver())).delete(s.key());
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
            log.info("Retention: {} file(s) of company {} deleted, {} failed", stored.size() - failed, companyId,
                    failed);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteBytes.run();
                }
            });
        } else {
            deleteBytes.run();
        }
    }
}
