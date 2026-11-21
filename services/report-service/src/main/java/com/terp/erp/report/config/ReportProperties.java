package com.terp.erp.report.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The services the dashboard and the calendar read, each with the caller's own token. */
@Validated
@ConfigurationProperties(prefix = "erp.report")
public record ReportProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                               @NotBlank String attendanceServiceUrl, @NotBlank String recruitmentServiceUrl,
                               @NotBlank String payrollServiceUrl, @NotBlank String assetServiceUrl,
                               @NotBlank String meetingServiceUrl) {
}
