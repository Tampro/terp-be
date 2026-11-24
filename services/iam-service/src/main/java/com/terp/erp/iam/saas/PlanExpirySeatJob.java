package com.terp.erp.iam.saas;

import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * When a trial or paid period ends, the company falls back to the default plan at read time
 * ({@link SubscriptionService#effective}); nothing happens at that moment, so this job applies the fallback plan's
 * user limit the same way a downgrade does ({@link PlanSeatEnforcer}). Idempotent: a company already within its limit
 * is left alone, so running it on several instances (no ShedLock yet) or repeatedly is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.saas.plan-expiry-seats.enabled", havingValue = "true", matchIfMissing = true)
public class PlanExpirySeatJob {
    private final CompanyRepository companies;
    private final PlanSeatEnforcer seats;
    private final Clock clock;

    @Scheduled(cron = "${erp.saas.plan-expiry-seats.cron:0 5 * * * *}")
    // lockAtLeastFor: the lock outlives a quick run, so an instance whose clock lags cannot run the job again
    @SchedulerLock(name = "iam.plan-expiry-seats", lockAtMostFor = "PT55M",
            lockAtLeastFor = "PT5M")
    public void run() {
        int locked = 0;
        for (Company c : companies.findByDeletedFalseAndPlanExpiresAtLessThanEqual(clock.instant())) {
            try {
                locked += seats.enforce(c); // one transaction per company: a failure stops only that company
            } catch (RuntimeException ex) {
                log.warn("Plan expiry: seat limit not applied to company {}: {}", c.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        if (locked > 0) {
            log.info("Plan expiry: locked {} account(s) over the fallback plan's limit", locked);
        }
    }
}
