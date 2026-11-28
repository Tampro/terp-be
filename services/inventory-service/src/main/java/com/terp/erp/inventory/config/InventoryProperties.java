package com.terp.erp.inventory.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @param iamServiceUrl     IAM, asked for the caller's authorities with the caller's own token
 * @param mediaServiceUrl   media-service: item images must be image files of the item's company
 * @param internalClientIds service-account clients (azp) allowed on /api/v1/internal/** (store-service storefront)
 */
@Validated
@ConfigurationProperties(prefix = "erp.inventory")
public record InventoryProperties(@NotBlank String iamServiceUrl, @NotBlank String mediaServiceUrl,
                                  @NotNull List<String> internalClientIds) {
}
