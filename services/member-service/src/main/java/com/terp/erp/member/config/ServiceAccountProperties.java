package com.terp.erp.member.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** member-service's own Keycloak client (client credentials) for the public storefront's internal reads. */
@Validated
@ConfigurationProperties(prefix = "erp.member.service-account")
public record ServiceAccountProperties(@NotBlank String tokenUrl, @NotBlank String clientId,
                                       @NotBlank String clientSecret) {
}
