package com.terp.erp.recruitment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl      IAM, asked for the caller's authorities with the caller's own token
 * @param employeeServiceUrl employee-service, called with the caller's token (branches, departments)
 * @param storageDir         resumes and cover letters (a docker volume), stored under server-generated names
 */
@Validated
@ConfigurationProperties(prefix = "erp.recruitment")
public record RecruitmentProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                                    @NotBlank String storageDir) {
}
