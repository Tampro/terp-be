package com.terp.erp.accounting.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl      IAM, asked for the caller's authorities with the caller's own token
 * @param commerceServiceUrl commerce-service: invoices and returns read, cleared payments applied, with the caller's token
 */
@Validated
@ConfigurationProperties(prefix = "erp.accounting")
public record AccountingProperties(@NotBlank String iamServiceUrl, @NotBlank String commerceServiceUrl) {
}
