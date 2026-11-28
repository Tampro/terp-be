package com.terp.erp.delivery.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** delivery-service's own Keycloak client (client credentials): order snapshot and rider check (internal APIs). */
@Validated
@ConfigurationProperties(prefix = "erp.delivery.service-account")
public record ServiceAccountProperties(@NotBlank String tokenUrl, @NotBlank String clientId,
                                       @NotBlank String clientSecret) {
}
