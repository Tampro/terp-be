package com.terp.erp.iam.email;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.email.EmailNotificationService.EnabledDto;
import com.terp.erp.iam.email.EmailNotificationService.NotificationDto;
import com.terp.erp.iam.email.EmailNotificationService.NotificationGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Company Settings → Email Notification Settings (docs/analysis/06 §3.3.1, ERP manual 19). */
@Tag(name = "Email notifications (company)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/email-notifications")
@SecurityRequirement(name = "bearerAuth")
public class EmailNotificationController {
    private final EmailNotificationService service;

    public record ToggleForm(@NotNull Boolean enabled) {
    }

    @Operation(summary = "My company's notification switches, grouped by module (all on unless switched off)",
            description = "Groups GENERAL, CRM, SUPPORT, RECRUITMENT, SUBSCRIPTION, ORDER (empty groups left out). "
                    + "dispatched=false: the switch is kept, but no email is sent for it yet.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @GetMapping
    public ApiMessageDto<List<NotificationGroup>> list(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.list(companyId));
    }

    @Operation(summary = "Turn one notification on or off for my company")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a switchable notification (EMAIL_003), no company (IAM_003)"),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.hasInCompany('NOTIFICATION_SETTINGS_UPDATE')")
    @PutMapping("/{code}")
    public ApiMessageDto<NotificationDto> toggle(@PathVariable String code, @Valid @RequestBody ToggleForm form) {
        return ApiMessageDto.ok(service.toggle(code, form.enabled()));
    }

    @Operation(summary = "Is this notification on for my company?", description = "For the services that will "
            + "send the email, called with the user's token. Any signed-in user of the company: it reveals one switch.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a switchable notification (EMAIL_003), no company (IAM_003)"),
            @ApiResponse(responseCode = "404", description = "Unknown code (EMAIL_001)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{code}/enabled")
    public ApiMessageDto<EnabledDto> enabled(@PathVariable String code,
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.enabled(companyId, code));
    }
}
