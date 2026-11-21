package com.terp.erp.meeting.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl      IAM, asked for the caller's authorities with the caller's own token
 * @param employeeServiceUrl employee-service, called with the caller's token (organizers, attendees, assignees)
 * @param encKey             MEETING_ENC_KEY: base64 of 32 random bytes, encrypts the Zoom client secrets
 * @param zoom               Zoom endpoints; overridable so tests can point at a stub
 */
@Validated
@ConfigurationProperties(prefix = "erp.meeting")
public record MeetingProperties(@NotBlank String iamServiceUrl, @NotBlank String employeeServiceUrl,
                                @NotBlank String encKey, @Valid @NotNull Zoom zoom) {
    public record Zoom(@NotBlank String oauthUrl, @NotBlank String apiUrl) {
    }
}
