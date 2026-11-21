package com.terp.erp.employee.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl   IAM, called with the caller's own token (authorities, accounts, company limits)
 * @param mediaServiceUrl media-service, called with the caller's own token (profile images, documents)
 * @param encKey          AES key of bank account number and tax payer id: base64 of 32 bytes, environment only.
 *                        Checked in FieldCipher, not here: a failed bean validation prints the value in the log.
 */
@Validated
@ConfigurationProperties(prefix = "erp.employee")
public record EmployeeProperties(
        @NotBlank String iamServiceUrl,
        @NotBlank String mediaServiceUrl,
        @NotBlank String encKey) {
}
