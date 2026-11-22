package com.terp.erp.iam.integration;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.integration.IntegrationDtos.AiForm;
import com.terp.erp.iam.integration.IntegrationDtos.AiView;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarForm;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarView;
import com.terp.erp.iam.integration.IntegrationDtos.IntegrationsView;
import com.terp.erp.iam.integration.IntegrationDtos.SlackForm;
import com.terp.erp.iam.integration.IntegrationDtos.SlackView;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramForm;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramView;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioForm;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioView;
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

@Tag(name = "Company integrations", description = "Slack, Telegram, Twilio, AI assistant, Google Calendar. "
        + "Secrets are write-only: views carry has… flags only.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/integrations")
@SecurityRequirement(name = "bearerAuth")
public class IntegrationController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NO_COMPANY = "Super admin without companyId (IAM_003)";
    private static final String SAVE = "Full replacement, except the secret: blank or omitted keeps the stored one. "
            + "enabled=true needs every field (SETTINGS_012). Needs ERP_SETTINGS_ENC_KEY to store a secret (SETTINGS_002).";
    private static final String SAVE_ERRORS = "Missing field to enable (SETTINGS_012), no encryption key "
            + "(SETTINGS_002), validation, " + NO_COMPANY;
    private static final String TEST = "Sends one sample message. Returns delivered, the HTTP status (null when no "
            + "answer) and failure TIMEOUT / CONNECTION_ERROR; the answer's body is never returned.";
    private static final String TEST_ERRORS = "Not configured (SETTINGS_017), URL refused (SETTINGS_013), stored "
            + "secret unreadable (SETTINGS_007), " + NO_COMPANY;

    private final CompanyIntegrationService service;

    @Operation(summary = "All integrations of the company (no secret)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<IntegrationsView> all(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.all(companyId));
    }

    @Operation(summary = "Save Slack", description = SAVE + " webhookUrl must be https://hooks.slack.com/… (SETTINGS_013).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "URL refused (SETTINGS_013), " + SAVE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/slack")
    public ApiMessageDto<SlackView> saveSlack(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                              @Valid @RequestBody SlackForm form) {
        return ApiMessageDto.ok(service.saveSlack(companyId, form));
    }

    @Operation(summary = "Test Slack", description = TEST)
    @ApiResponses(@ApiResponse(responseCode = "400", description = TEST_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/slack/test")
    public ApiMessageDto<TestResult> testSlack(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.testSlack(companyId));
    }

    @Operation(summary = "Save Telegram", description = SAVE)
    @ApiResponses(@ApiResponse(responseCode = "400", description = SAVE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/telegram")
    public ApiMessageDto<TelegramView> saveTelegram(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                    @Valid @RequestBody TelegramForm form) {
        return ApiMessageDto.ok(service.saveTelegram(companyId, form));
    }

    @Operation(summary = "Test Telegram", description = TEST + " Only to api.telegram.org.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bot token malformed (SETTINGS_019), " + TEST_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/telegram/test")
    public ApiMessageDto<TestResult> testTelegram(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.testTelegram(companyId));
    }

    @Operation(summary = "Save Twilio", description = SAVE)
    @ApiResponses(@ApiResponse(responseCode = "400", description = SAVE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/twilio")
    public ApiMessageDto<TwilioView> saveTwilio(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                @Valid @RequestBody TwilioForm form) {
        return ApiMessageDto.ok(service.saveTwilio(companyId, form));
    }

    @Operation(summary = "Save AI assistant", description = SAVE)
    @ApiResponses(@ApiResponse(responseCode = "400", description = SAVE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/ai-assistant")
    public ApiMessageDto<AiView> saveAi(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                        @Valid @RequestBody AiForm form) {
        return ApiMessageDto.ok(service.saveAi(companyId, form));
    }

    @Operation(summary = "Save Google Calendar", description = SAVE + " serviceAccountJson: the key file of a Google "
            + "service account (type service_account, client_email, private_key).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a service account key (SETTINGS_016), " + SAVE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/google-calendar")
    public ApiMessageDto<GoogleCalendarView> saveGoogleCalendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody GoogleCalendarForm form) {
        return ApiMessageDto.ok(service.saveGoogleCalendar(companyId, form));
    }

    @Operation(summary = "Remove an integration and its secrets", description = "type: slack, telegram, twilio, "
            + "ai-assistant, google-calendar. Idempotent.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown type (SETTINGS_018), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping("/{type}")
    public ApiMessageDto<Void> delete(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                      @PathVariable String type) {
        service.delete(companyId, type);
        return ApiMessageDto.ok(null);
    }
}
