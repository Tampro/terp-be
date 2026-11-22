package com.terp.erp.notification.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param iamServiceUrl   IAM: the caller's authorities (user token) and the internal channel settings / contacts
 *                        (service-account token)
 * @param exchange        topic exchange the producers publish to (erp-common OutboxProperties.DEFAULT_EXCHANGE)
 * @param queue           durable queue of this service, bound to the exchange with "#"
 * @param deadLetterQueue where a message goes after the listener retries are exhausted
 * @param maxAttempts     send tries of one delivery before it is FAILED
 * @param initialBackoff  wait after the first failed send; doubled after each further failure
 * @param maxBackoff      cap of the wait
 * @param retryBatchSize  due deliveries resent per run of the retry job
 */
@Validated
@ConfigurationProperties(prefix = "erp.notification")
public record NotificationProperties(
        @NotBlank String iamServiceUrl,
        @NotBlank String exchange,
        @NotBlank String queue,
        @NotBlank String deadLetterQueue,
        @Min(1) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        @Min(1) int retryBatchSize) {

    /** initialBackoff * 2^(attempts-1), capped at maxBackoff. */
    public Duration backoff(int attempts) {
        Duration d = initialBackoff;
        for (int i = 1; i < attempts && d.compareTo(maxBackoff) < 0; i++) {
            d = d.multipliedBy(2);
        }
        return d.compareTo(maxBackoff) > 0 ? maxBackoff : d;
    }
}
