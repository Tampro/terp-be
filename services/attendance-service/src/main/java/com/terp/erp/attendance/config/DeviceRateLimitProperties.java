package com.terp.erp.attendance.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits of the biometric device endpoint (the only one reachable without a token).
 *
 * @param perMinute       requests per minute per device key
 * @param burst           requests a device may send at once
 * @param failedPerMinute requests with a wrong key per minute per client IP before that IP gets 429 (slows key
 *                        guessing)
 * @param trustForwardedFor take the client IP from the last X-Forwarded-For entry (added by the gateway). Only when
 *                          the service is reachable through the gateway alone; otherwise a client could forge it.
 */
@Validated
@ConfigurationProperties(prefix = "erp.attendance.device-rate-limit")
public record DeviceRateLimitProperties(@Min(1) int perMinute, @Min(1) int burst, @Min(1) int failedPerMinute,
                                        boolean trustForwardedFor) {
}
