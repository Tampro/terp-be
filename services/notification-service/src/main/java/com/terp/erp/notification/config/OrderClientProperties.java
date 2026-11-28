package com.terp.erp.notification.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param serviceUrl order-service: who to email about an order and what to say (internal, service account) */
@Validated
@ConfigurationProperties(prefix = "erp.notification.order")
public record OrderClientProperties(@NotBlank String serviceUrl) {
}
