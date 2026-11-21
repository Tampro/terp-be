package com.terp.erp.employee.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak service account of employee-service (client credentials), used only where no caller token can do the job:
 * media links of announcement attachments for employees without MEDIA_VIEW, after the audience check.
 *
 * @param tokenUrl     Keycloak token endpoint (inside the network)
 * @param clientId     confidential client with service accounts enabled (realm erp)
 * @param clientSecret environment only, no default
 */
@Validated
@ConfigurationProperties(prefix = "erp.employee.service-account")
public record ServiceAccountProperties(
        @NotBlank String tokenUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret) {
}
