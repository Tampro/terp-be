package com.terp.erp.delivery.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** @param clientIds service-account clients (azp) allowed on /api/v1/internal/** (order-service: cancel before pick-up) */
@Validated
@ConfigurationProperties(prefix = "erp.delivery.internal")
public record InternalClientProperties(@NotNull List<String> clientIds) {
}
