package com.terp.erp.iam.internal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.internal.InternalNotificationService.Contact;
import com.terp.erp.iam.internal.InternalNotificationService.NotificationConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only: accepted for the service-account tokens of erp.iam.internal-client-ids (SecurityConfig
 * internalChain), not routed by the gateway. No @PreAuthorize: the caller is a service account, not an IAM user;
 * the chain is the authorization. Answers carry decrypted secrets — never log them.
 */
@Tag(name = "Internal")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
@SecurityRequirement(name = "bearerAuth")
public class InternalNotificationController {
    private final InternalNotificationService service;

    @Operation(summary = "Channel settings of a company for one event (notification-service)",
            description = "eventCode: an email notification code (ENotificationEvent), optional; webhookEvent: a "
                    + "webhook event code (EWebhookEvent), optional. smtp is null when the email switch is off or "
                    + "no SMTP is configured; slack / telegram / twilio are null when the integration is off. eventType: "
                    + "the event's routing key, optional; slackEnabledForEvent / telegramEnabledForEvent / "
                    + "smsEnabledForEvent are the company's per-event switches for it (true when not set).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown eventCode (EMAIL_001) or webhookEvent (SETTINGS_014)"),
            @ApiResponse(responseCode = "401", description = "No or invalid token (COMMON_003)"),
            @ApiResponse(responseCode = "403", description = "Token not of an allowed service account (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such company (IAM_001)")})
    @GetMapping("/notification-config")
    public ApiMessageDto<NotificationConfig> notificationConfig(@RequestParam Long companyId,
                                                                @RequestParam(required = false) String eventType,
                                                                @RequestParam(required = false) String eventCode,
                                                                @RequestParam(required = false) String webhookEvent) {
        return ApiMessageDto.ok(service.config(companyId, eventType, eventCode, webhookEvent));
    }

    @Operation(summary = "Name and email of a user, to address a notification (notification-service)")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "No or invalid token (COMMON_003)"),
            @ApiResponse(responseCode = "403", description = "Token not of an allowed service account (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such user (IAM_009)")})
    @GetMapping("/users/{id}/contact")
    public ApiMessageDto<Contact> contact(@PathVariable Long id) {
        return ApiMessageDto.ok(service.contact(id));
    }
}
