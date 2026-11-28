package com.terp.erp.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl       IAM: authorities of a staff caller (staff chain)
 * @param memberServiceUrl    member-service: the caller's membership of a shop (internal, service account)
 * @param storeServiceUrl     store-service: fees and minimums of a store (internal, service account)
 * @param inventoryServiceUrl inventory-service: live price / limit / sold-out of the cart lines (internal, service account)
 * @param customerJwkSetUri   keys of realm erp-customer
 * @param customerIssuer      issuer of realm erp-customer, the only one accepted on /api/v1/shop/**
 */
@Validated
@ConfigurationProperties(prefix = "erp.order")
public record OrderProperties(@NotBlank String iamServiceUrl, @NotBlank String memberServiceUrl,
                              @NotBlank String storeServiceUrl, @NotBlank String inventoryServiceUrl,
                              @NotBlank String customerJwkSetUri, @NotBlank String customerIssuer) {
}
