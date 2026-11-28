package com.terp.erp.order.cart;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** FR-ORD-001 / 009: a cart line untouched for 90 days goes, unless the customer kept it ("계속보관"). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartCleanupJob {
    static final Duration KEEP_FOR = Duration.ofDays(90);

    private final CartLineRepository lines;
    private final Clock clock;

    @Scheduled(cron = "${erp.order.cart-cleanup-cron:0 0 4 * * *}", zone = "UTC")
    @SchedulerLock(name = "order.cart-cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public int run() {
        Instant before = clock.instant().minus(KEEP_FOR);
        int removed = lines.deleteStale(before);
        log.info("cart clean-up: {} line(s) older than {} removed", removed, before);
        return removed;
    }
}
