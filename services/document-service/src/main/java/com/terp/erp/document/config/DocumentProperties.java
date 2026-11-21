package com.terp.erp.document.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl      IAM, asked for the caller's authorities with the caller's own token
 * @param employeeServiceUrl employee-service, called with the caller's token (contract employees, assignees)
 * @param mediaServiceUrl    media-service, called with the caller's token (document files)
 */
@Validated
@ConfigurationProperties(prefix = "erp.document")
public record DocumentProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                                 @NotBlank String mediaServiceUrl) {
}
