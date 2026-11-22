package com.terp.erp.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak service account of notification-service (client credentials), used only for IAM's internal endpoints
 * (channel settings with decrypted secrets, recipient contacts): there is no user behind a broker message.
 *
 * @param tokenUrl     Keycloak token endpoint (inside the network)
 * @param clientId     confidential client with service accounts enabled (realm erp)
 * @param clientSecret environment only, no default
 */
@Validated
@ConfigurationProperties(prefix = "erp.notification.service-account")
public record ServiceAccountProperties(
        @NotBlank String tokenUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret) {
}
