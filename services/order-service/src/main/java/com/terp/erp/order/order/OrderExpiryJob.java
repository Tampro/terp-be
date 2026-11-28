package com.terp.erp.order.order;

import com.terp.erp.order.coupon.CouponLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An order left unpaid for 15 minutes expires (no stock was taken). One transaction per order: its coupon use, if any,
 * is given back with the status change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {
    // TODO(assumption): 15-minute payment window (checkout spec).
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    private final CustomerOrderRepository orders;
    private final CouponLedger coupons;
    private final PlatformTransactionManager txManager;
    private final Clock clock;

    @Scheduled(cron = "${erp.order.expiry-cron:0 */5 * * * *}", zone = "UTC")
    @SchedulerLock(name = "order.expire-unpaid", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public int run() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        int n = 0;
        for (Long id : orders.idsToExpire(clock.instant().minus(PAYMENT_WINDOW))) {
            Boolean done = tx.execute(st -> orders.findById(id)
                    .filter(o -> o.getStatus() == EOrderStatus.PENDING_PAYMENT)
                    .map(o -> {
                        o.setStatus(EOrderStatus.EXPIRED);
                        orders.save(o);
                        coupons.release(o.getId());
                        return true;
                    }).orElse(false));
            if (Boolean.TRUE.equals(done)) {
                n++;
            }
        }
        if (n > 0) {
            log.info("orders expired: {}", n);
        }
        return n;
    }
}
