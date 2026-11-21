package com.terp.erp.commerce.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl        IAM, asked for the caller's authorities with the caller's own token
 * @param inventoryServiceUrl  inventory-service: items (prices, taxes) and stock moves, with the caller's token
 * @param accountingServiceUrl accounting-service: customers, with the caller's token
 */
@Validated
@ConfigurationProperties(prefix = "erp.commerce")
public record CommerceProperties(@NotBlank String iamServiceUrl, @NotBlank String inventoryServiceUrl,
                                 @NotBlank String accountingServiceUrl) {
}
