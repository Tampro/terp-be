package com.terp.erp.inventory.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl   IAM, asked for the caller's authorities with the caller's own token
 * @param mediaServiceUrl media-service: item images must be image files of the item's company
 */
@Validated
@ConfigurationProperties(prefix = "erp.inventory")
public record InventoryProperties(@NotBlank String iamServiceUrl, @NotBlank String mediaServiceUrl) {
}
