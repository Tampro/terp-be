package com.terp.erp.employee.lifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sets EXPIRED on warnings in force past their expiry date, every night (PO 2026-11-21). Idempotent, so running on
 * several instances at once is harmless (no ShedLock yet); the read-time "expired" flag covers the hours in between.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarningExpiryJob {
    private final WarningService warnings;

    @Scheduled(cron = "${erp.jobs.expiry-cron:0 10 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        try {
            int n = warnings.expireOverdue();
            log.info("Warning expiry job: {} warning(s) set to EXPIRED", n);
        } catch (RuntimeException e) {
            log.error("Warning expiry job failed: {}", e.getClass().getSimpleName());
        }
    }
}
