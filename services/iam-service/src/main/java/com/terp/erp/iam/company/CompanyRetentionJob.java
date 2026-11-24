package com.terp.erp.iam.company;

import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Daily: anonymises up to 50 companies deleted at least {@code erp.saas.retention.years} (default 5, PO 2026-11-24)
 * ago, oldest first, each in its own transaction (a failure skips only that company; it is retried the next day).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.saas.retention.enabled", havingValue = "true", matchIfMissing = true)
public class CompanyRetentionJob {
    private final CompanyRepository companies;
    private final CompanyAnonymizer anonymizer;
    private final Clock clock;

    @Value("${erp.saas.retention.years:5}")
    private int years;

    @Scheduled(cron = "${erp.saas.retention.cron:0 30 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    // lockAtLeastFor: the lock outlives a quick run, so an instance whose clock lags cannot run the job again
    @SchedulerLock(name = "iam.company-retention", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void run() {
        var cutoff = clock.instant().atZone(ZoneOffset.UTC).minusYears(years).toInstant();
        for (Company c : companies.findTop50ByDeletedTrueAndAnonymizedAtIsNullAndDeletedAtLessThanEqualOrderByDeletedAtAsc(
                cutoff)) {
            try {
                anonymizer.anonymize(c);
            } catch (RuntimeException ex) {
                log.warn("Retention: company {} not anonymised: {}", c.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
