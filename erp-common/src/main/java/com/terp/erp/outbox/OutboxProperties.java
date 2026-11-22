package com.terp.erp.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * erp.outbox.* of a producing service.
 *
 * @param enabled        opt-in switch: the outbox beans exist only when true
 * @param exchange       topic exchange the events go to; routing key = event type
 * @param batchSize      rows locked and published per relay transaction
 * @param pollIntervalMs delay between two relay runs (read by the @Scheduled of {@link OutboxRelay})
 * @param maxAttempts    publish tries before a row becomes FAILED (final)
 * @param initialBackoff wait after the first failure; doubled after each further failure
 * @param maxBackoff     cap of the wait
 * @param confirmTimeout how long one publish waits for the broker's confirm
 */
@ConfigurationProperties(prefix = "erp.outbox")
public record OutboxProperties(boolean enabled, String exchange, Integer batchSize, Long pollIntervalMs,
                               Integer maxAttempts, Duration initialBackoff, Duration maxBackoff,
                               Duration confirmTimeout) {
    public static final String DEFAULT_EXCHANGE = "erp.events";

    public OutboxProperties {
        exchange = exchange == null || exchange.isBlank() ? DEFAULT_EXCHANGE : exchange;
        batchSize = batchSize == null || batchSize < 1 ? 20 : batchSize;
        pollIntervalMs = pollIntervalMs == null || pollIntervalMs < 100 ? 2000L : pollIntervalMs;
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 10 : maxAttempts;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(5) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(10) : maxBackoff;
        confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
    }
}
