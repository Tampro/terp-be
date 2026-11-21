package com.terp.erp.crm.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl        IAM, asked for the caller's authorities with the caller's own token
 * @param inventoryServiceUrl  inventory-service: items linked to leads and deals
 * @param mediaServiceUrl      media-service: files attached to leads, deals and contracts
 * @param accountingServiceUrl accounting-service: customers (deal clients, contract parties)
 */
@Validated
@ConfigurationProperties(prefix = "erp.crm")
public record CrmProperties(@NotBlank String iamServiceUrl, @NotBlank String inventoryServiceUrl,
                           @NotBlank String mediaServiceUrl, @NotBlank String accountingServiceUrl) {
}
