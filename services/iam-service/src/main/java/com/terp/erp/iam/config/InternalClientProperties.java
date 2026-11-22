package com.terp.erp.iam.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @param internalClientIds the Keycloak clients (token azp) allowed on /api/v1/internal/**, one per calling service
 *                          (today notification-service: channel configuration and recipient contacts)
 */
@Validated
@ConfigurationProperties(prefix = "erp.iam")
public record InternalClientProperties(@NotEmpty List<String> internalClientIds) {
}
