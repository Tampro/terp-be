package com.terp.erp.commerce.sales;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily trigger of {@link PaymentReminderService}. Scheduling is enabled by the erp-common outbox
 * auto-configuration (the reminder needs the outbox anyway).
 * TODO(assumption): one commerce-service instance (no ShedLock). With two, both would run; the unique key on
 * payment_reminder_log then makes the later transaction fail instead of sending twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "erp.commerce.payment-reminder", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class PaymentReminderJob {
    private final PaymentReminderService reminders;
    private final Clock clock;

    @Value("${erp.commerce.payment-reminder.zone:Asia/Ho_Chi_Minh}")
    private String zone;

    @Scheduled(cron = "${erp.commerce.payment-reminder.cron:0 0 8 * * *}",
            zone = "${erp.commerce.payment-reminder.zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = "commerce.payment-reminder", lockAtMostFor = "PT1H")
    public void run() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(zone)));
        int sent = reminders.remind(today);
        log.info("payment reminders for {}: {}", today, sent);
    }
}
