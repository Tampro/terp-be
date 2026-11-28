package com.terp.erp.store.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** @param clientIds service-account clients (azp) allowed on /api/v1/internal/** (member-service: shop of a slug) */
@Validated
@ConfigurationProperties(prefix = "erp.store.internal")
public record InternalClientProperties(@NotNull List<String> clientIds) {
}
