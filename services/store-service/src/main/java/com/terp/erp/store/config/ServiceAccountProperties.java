package com.terp.erp.store.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** store-service's own Keycloak client (client credentials) for the public storefront's internal reads. */
@Validated
@ConfigurationProperties(prefix = "erp.store.service-account")
public record ServiceAccountProperties(@NotBlank String tokenUrl, @NotBlank String clientId,
                                       @NotBlank String clientSecret) {
}
