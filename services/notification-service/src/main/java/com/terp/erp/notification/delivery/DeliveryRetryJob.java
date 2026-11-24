package com.terp.erp.notification.delivery;

import com.terp.erp.notification.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Resends PENDING deliveries whose back-off is over (and new ones the consumer could not send, after a grace). */
@Component
@RequiredArgsConstructor
public class DeliveryRetryJob {
    private final DeliveryRepository deliveries;
    private final DeliveryDispatcher dispatcher;
    private final NotificationProperties props;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${erp.notification.retry-interval-ms:30000}",
            initialDelayString = "${erp.notification.retry-interval-ms:30000}")
    @SchedulerLock(name = "notification.delivery-retry", lockAtMostFor = "PT5M")
    public void run() {
        List<Long> due = deliveries.findDueIds(Instant.now(clock), PageRequest.of(0, props.retryBatchSize()));
        if (!due.isEmpty()) {
            dispatcher.dispatch(due);
        }
    }
}
