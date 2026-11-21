package com.terp.erp.asset.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl      IAM, asked for the caller's authorities with the caller's own token
 * @param employeeServiceUrl employee-service, called with the caller's token (assignees)
 * @param mediaServiceUrl    media-service, called with the caller's token (asset images and documents)
 */
@Validated
@ConfigurationProperties(prefix = "erp.asset")
public record AssetProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                              @NotBlank String mediaServiceUrl) {
}
