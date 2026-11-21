package com.terp.erp.iam.email;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.security.TenantGuard;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Company Settings → Email Notification Settings (docs/analysis/06 §3.3.1): one switch per template. */
@Tag(name = "Email notifications (company)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/email-notifications")
@SecurityRequirement(name = "bearerAuth")
public class EmailNotificationController {
    private final CompanyEmailNotificationRepository toggles;
    private final EmailTemplateRepository templates;

    public record NotificationDto(String code, String name, boolean enabled) {
    }

    public record ToggleForm(@NotNull Boolean enabled) {
    }

    @Operation(summary = "My company's notification switches (all on unless switched off)")
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_SETTINGS_UPDATE')")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiMessageDto<List<NotificationDto>> list(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId) {
        Long cid = TenantGuard.resolveCompany(companyId, EIamError.IAM_003);
        Map<String, Boolean> off = toggles.findByCompanyId(cid).stream()
                .collect(Collectors.toMap(CompanyEmailNotification::getTemplateCode, CompanyEmailNotification::isEnabled));
        Map<String, String> names = templates.findAll().stream()
                .collect(Collectors.toMap(EmailTemplate::getCode, EmailTemplate::getName));
        return ApiMessageDto.ok(Arrays.stream(EEmailTemplate.values()).filter(EEmailTemplate::isToggleable)
                .map(t -> new NotificationDto(t.name(), names.getOrDefault(t.name(), t.name()),
                        off.getOrDefault(t.name(), true)))
                .toList());
    }

    @Operation(summary = "Turn one notification on or off for my company")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a switchable notification (EMAIL_003)"),
            @ApiResponse(responseCode = "404", description = "Unknown template code (EMAIL_001)")})
    @PreAuthorize("@PermissionChecker.hasInCompany('NOTIFICATION_SETTINGS_UPDATE')")
    @PutMapping("/{code}")
    @Transactional
    public ApiMessageDto<NotificationDto> toggle(@PathVariable String code, @Valid @RequestBody ToggleForm form) {
        EEmailTemplate t = Arrays.stream(EEmailTemplate.values()).filter(x -> x.name().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound(EEmailError.EMAIL_001));
        if (!t.isToggleable()) {
            throw ApiException.badRequest(EEmailError.EMAIL_003);
        }
        Long cid = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        CompanyEmailNotification row = toggles.findByCompanyIdAndTemplateCode(cid, code).orElseGet(() -> {
            CompanyEmailNotification n = new CompanyEmailNotification();
            n.setCompanyId(cid);
            n.setTemplateCode(code);
            return n;
        });
        row.setEnabled(form.enabled());
        toggles.save(row);
        String name = templates.findByCode(code).map(EmailTemplate::getName).orElse(code);
        return ApiMessageDto.ok(new NotificationDto(code, name, form.enabled()));
    }
}
