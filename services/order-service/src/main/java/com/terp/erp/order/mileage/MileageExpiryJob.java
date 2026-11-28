package com.terp.erp.order.mileage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Daily: what is left of each overdue lot expires (one transaction per lot). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MileageExpiryJob {
    private final MileageLotRepository lots;
    private final MileageLedger ledger;
    private final PlatformTransactionManager txManager;
    private final Clock clock;

    @Scheduled(cron = "${erp.order.mileage-expiry-cron:0 10 3 * * *}", zone = "Asia/Seoul")
    @SchedulerLock(name = "mileage.expire", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public int run() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        int points = 0;
        for (Long id : lots.overdue(clock.instant())) {
            Integer n = tx.execute(st -> ledger.expire(id));
            points += n == null ? 0 : n;
        }
        if (points > 0) {
            log.info("mileage points expired: {}", points);
        }
        return points;
    }
}
