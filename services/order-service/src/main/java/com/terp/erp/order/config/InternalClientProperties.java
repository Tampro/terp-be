package com.terp.erp.order.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** @param clientIds service-account clients (azp) allowed on /api/v1/internal/** (delivery-service: the order behind a delivery) */
@Validated
@ConfigurationProperties(prefix = "erp.order.internal")
public record InternalClientProperties(@NotNull List<String> clientIds) {
}
