package com.terp.erp.order.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/** An order left unpaid for 15 minutes expires (no stock was taken, so nothing to give back). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {
    // TODO(assumption): 15-minute payment window (checkout spec).
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    private final CustomerOrderRepository orders;
    private final Clock clock;

    @Scheduled(cron = "${erp.order.expiry-cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "order.expire-unpaid", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    @Transactional
    public int run() {
        int n = orders.expire(clock.instant().minus(PAYMENT_WINDOW));
        if (n > 0) {
            log.info("orders expired: {}", n);
        }
        return n;
    }
}
