package com.terp.erp.member.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl       IAM: authorities of a staff caller (staff chain)
 * @param storeServiceUrl     store-service: the company of a shop slug (internal, service account)
 * @param customerJwkSetUri   keys of realm erp-customer
 * @param customerIssuer      issuer of realm erp-customer, the only one accepted on /api/v1/shop/**
 * @param encKey              MEMBER_ENC_KEY, AES-256-GCM key of the address phone (base64 of 32 bytes)
 */
@Validated
@ConfigurationProperties(prefix = "erp.member")
public record MemberProperties(@NotBlank String iamServiceUrl, @NotBlank String storeServiceUrl,
                               @NotBlank String customerJwkSetUri, @NotBlank String customerIssuer,
                               @NotBlank String encKey) {
}
