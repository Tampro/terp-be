package com.terp.erp.recruitment.offer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sets EXPIRED on open offers past their expiration date, every night (PO 2026-11-21). Idempotent, so running on
 * several instances at once is harmless (no ShedLock yet); the read-time "expired" flag covers the hours in between.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferExpiryJob {
    private final OfferService offers;

    @Scheduled(cron = "${erp.jobs.expiry-cron:0 10 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    @SchedulerLock(name = "recruitment.offer-expiry", lockAtMostFor = "PT1H")
    public void run() {
        try {
            int n = offers.expireOverdue();
            log.info("Offer expiry job: {} offer(s) set to EXPIRED", n);
        } catch (RuntimeException e) {
            log.error("Offer expiry job failed: {}", e.getClass().getSimpleName());
        }
    }
}
