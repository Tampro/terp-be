package com.terp.erp.iam.template;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.template.NotificationTemplateService.ChatPreview;
import com.terp.erp.iam.template.NotificationTemplateService.ChatPreviewForm;
import com.terp.erp.iam.template.NotificationTemplateService.ChatTemplateForm;
import com.terp.erp.iam.template.NotificationTemplateService.ChatTemplateView;
import com.terp.erp.iam.template.NotificationTemplateService.ChatTemplatesView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company Settings → Notification templates (ERP manual 3.5): Slack, Telegram and Twilio SMS tabs, one plain-text
 * line per routed event and language, edit only. Same permission as the per-event switches of these channels.
 */
@Tag(name = "Notification templates (company)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/notification-templates/{channel}")
@SecurityRequirement(name = "bearerAuth")
public class ChatNotificationTemplateController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String CHANNEL = "slack, telegram or sms";
    private static final String EVENT = "Routing key of the event, e.g. support.ticket.created";
    private static final String LANG = "vi, en or ko";
    private static final String COMMON = "unknown channel (SETTINGS_020), super admin without companyId (IAM_003)";

    private final NotificationTemplateService service;

    @Operation(summary = "Events of the channel grouped by module, with the languages my company customised",
            description = "Visible even when the integration is not configured (configured=false, read-only).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown channel (SETTINGS_020), no company "
            + "(IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<ChatTemplatesView> list(@Parameter(description = CHANNEL) @PathVariable String channel,
                                                 @Parameter(description = COMPANY) @RequestParam(required = false)
                                                 Long companyId) {
        return ApiMessageDto.ok(service.chatList(companyId, channel));
    }

    @Operation(summary = "One event: variables and my company's text per language (null = default)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown channel (SETTINGS_020), no company (IAM_003)"),
            @ApiResponse(responseCode = "404", description = "Unknown event (SETTINGS_021)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/{eventType}")
    public ApiMessageDto<ChatTemplateView> get(@Parameter(description = CHANNEL) @PathVariable String channel,
                                               @Parameter(description = EVENT) @PathVariable String eventType,
                                               @Parameter(description = COMPANY) @RequestParam(required = false)
                                               Long companyId) {
        return ApiMessageDto.ok(service.chatGet(companyId, channel, eventType));
    }

    @Operation(summary = "Save my company's text for one language",
            description = "Full replacement of the text for that language. Plain text, at most 1000 characters "
                    + "(Slack, Telegram) or 1600 (SMS); placeholders must be in variables. Refused while the "
                    + "integration is not configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Integration not configured (SETTINGS_022), too long "
                    + "(SETTINGS_024), unknown placeholder (SETTINGS_025), unsupported language (EMAIL_002), "
                    + "validation (COMMON_001), " + COMMON),
            @ApiResponse(responseCode = "404", description = "Unknown event (SETTINGS_021)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/{eventType}/{lang}")
    public ApiMessageDto<ChatTemplateView> save(@Parameter(description = CHANNEL) @PathVariable String channel,
                                                @Parameter(description = EVENT) @PathVariable String eventType,
                                                @Parameter(description = LANG) @PathVariable String lang,
                                                @Parameter(description = COMPANY) @RequestParam(required = false)
                                                Long companyId,
                                                @Valid @RequestBody ChatTemplateForm form) {
        return ApiMessageDto.ok(service.chatSave(companyId, channel, eventType, lang, form));
    }

    @Operation(summary = "Reset one language to the built-in default", description = "Deletes the override; "
            + "no override = no change. Allowed while the integration is not configured.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unsupported language (EMAIL_002), " + COMMON),
            @ApiResponse(responseCode = "404", description = "Unknown event (SETTINGS_021)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping("/{eventType}/{lang}")
    public ApiMessageDto<ChatTemplateView> reset(@Parameter(description = CHANNEL) @PathVariable String channel,
                                                 @Parameter(description = EVENT) @PathVariable String eventType,
                                                 @Parameter(description = LANG) @PathVariable String lang,
                                                 @Parameter(description = COMPANY) @RequestParam(required = false)
                                                 Long companyId) {
        return ApiMessageDto.ok(service.chatReset(companyId, channel, eventType, lang));
    }

    @Operation(summary = "Render with sample values [key], nothing is sent",
            description = "The text in the request, else my company's saved text.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Nothing to preview (SETTINGS_023), unsupported "
                    + "language (EMAIL_002), " + COMMON),
            @ApiResponse(responseCode = "404", description = "Unknown event (SETTINGS_021)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/{eventType}/{lang}/preview")
    public ApiMessageDto<ChatPreview> preview(@Parameter(description = CHANNEL) @PathVariable String channel,
                                              @Parameter(description = EVENT) @PathVariable String eventType,
                                              @Parameter(description = LANG) @PathVariable String lang,
                                              @Parameter(description = COMPANY) @RequestParam(required = false)
                                              Long companyId,
                                              @Valid @RequestBody(required = false) ChatPreviewForm form) {
        return ApiMessageDto.ok(service.chatPreview(companyId, channel, eventType, lang, form));
    }
}
