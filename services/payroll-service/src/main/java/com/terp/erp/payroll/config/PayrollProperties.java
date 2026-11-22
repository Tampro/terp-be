package com.terp.erp.payroll.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl        IAM, asked for the caller's authorities with the caller's own token
 * @param employeeServiceUrl   employee-service, called with the caller's token (employees, base salary)
 * @param attendanceServiceUrl attendance-service, called with the caller's token (attendance of a pay period)
 * @param accountingServiceUrl accounting-service, called with the caller's token (bank accounts, payroll payment)
 */
@Validated
@ConfigurationProperties(prefix = "erp.payroll")
public record PayrollProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                                @NotBlank String attendanceServiceUrl,
                                @NotBlank String accountingServiceUrl) {
}
