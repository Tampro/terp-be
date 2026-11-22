package com.terp.erp.project.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl        IAM, asked for the caller's authorities with the caller's own token; project team users
 * @param mediaServiceUrl      media-service: project attachments
 * @param crmServiceUrl        crm-service: form responses converted to leads, deals or contracts
 * @param accountingServiceUrl accounting-service: project clients (customers)
 */
@Validated
@ConfigurationProperties(prefix = "erp.project")
public record ProjectProperties(@NotBlank String iamServiceUrl, @NotBlank String mediaServiceUrl,
                                @NotBlank String crmServiceUrl, @NotBlank String accountingServiceUrl) {
}
