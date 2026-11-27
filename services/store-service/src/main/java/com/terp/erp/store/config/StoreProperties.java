package com.terp.erp.store.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl       IAM: the caller's authorities (with the caller's own token)
 * @param inventoryServiceUrl inventory-service: the warehouse a store sells from, checked with the caller's token
 */
@Validated
@ConfigurationProperties(prefix = "erp.store")
public record StoreProperties(@NotBlank String iamServiceUrl, @NotBlank String inventoryServiceUrl) {
}
