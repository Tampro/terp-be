package com.terp.erp.media.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * @param storageDir  root directory of the files (a docker volume); files are stored under server-generated names
 * @param linkSecret  HMAC key of the time-limited file links — from the environment only, at least 32 chars
 * @param linkTtl     how long a view/download link works
 * @param maxFileSize largest accepted upload
 * @param publicBaseUrl base the FE uses to open links (the gateway), e.g. http://localhost:8080
 * @param internalClientIds the Keycloak clients (token azp) allowed on /api/v1/internal/**, one per calling service
 */
@Validated
@ConfigurationProperties(prefix = "erp.media")
public record MediaProperties(
        @NotBlank String storageDir,
        // Length checked in LinkSigner, not with @Size: a failed bean validation prints the value in the log.
        @NotBlank String linkSecret,
        @NotNull Duration linkTtl,
        @NotNull DataSize maxFileSize,
        @NotBlank String publicBaseUrl,
        @NotBlank String iamServiceUrl,
        @NotEmpty List<String> internalClientIds) {
}
