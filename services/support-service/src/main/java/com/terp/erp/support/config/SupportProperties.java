package com.terp.erp.support.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl   IAM: the caller's authorities (with the caller's own token) and messenger peers
 * @param mediaServiceUrl media-service: ticket / reply / message attachments, checked per company
 */
@Validated
@ConfigurationProperties(prefix = "erp.support")
public record SupportProperties(@NotBlank String iamServiceUrl, @NotBlank String mediaServiceUrl) {
}
