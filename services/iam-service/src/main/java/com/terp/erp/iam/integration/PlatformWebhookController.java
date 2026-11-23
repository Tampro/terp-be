package com.terp.erp.iam.integration;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.integration.PlatformWebhookService.PlatformEventDto;
import com.terp.erp.iam.integration.PlatformWebhookService.PlatformWebhookDto;
import com.terp.erp.iam.integration.PlatformWebhookService.PlatformWebhookForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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

import java.util.List;

@Tag(name = "Platform webhooks", description = "Super admin webhooks for platform events. POST of JSON {event, "
        + "eventName, module: PLATFORM, eventId, companyId: null, occurredAt, data}; signed like company webhooks "
        + "when a secret is set. Delivery log: GET /api/v1/notifications/deliveries?platform=true.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/admin/webhooks")
@SecurityRequirement(name = "bearerAuth")
public class PlatformWebhookController {
    private static final String FORM_ERRORS = "Unknown event (SETTINGS_014), URL refused (SETTINGS_013), secret "
            + "shorter than 16 (SETTINGS_019), no encryption key (SETTINGS_002), validation (COMMON_001)";
    private static final String NOT_SUPER = "Not a super admin (COMMON_004)";
    private static final String NOT_FOUND = "No such platform webhook (SETTINGS_026)";

    private final PlatformWebhookService service;

    @Operation(summary = "Platform event catalog")
    @ApiResponses(@ApiResponse(responseCode = "403", description = NOT_SUPER))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/events")
    public ApiMessageDto<List<PlatformEventDto>> events() {
        return ApiMessageDto.ok(service.events());
    }

    @Operation(summary = "Platform webhooks")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown event filter (SETTINGS_014)"),
            @ApiResponse(responseCode = "403", description = NOT_SUPER)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PlatformWebhookDto>> list(
            @Parameter(description = "Event code filter") @RequestParam(required = false) String eventCode,
            @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(eventCode, pageable)));
    }

    @Operation(summary = "One platform webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = NOT_SUPER),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/{id}")
    public ApiMessageDto<PlatformWebhookDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a platform webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "403", description = NOT_SUPER)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping
    public ApiMessageDto<PlatformWebhookDto> create(@Valid @RequestBody PlatformWebhookForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Update a platform webhook", description = "Full replacement, except the secret: blank "
            + "keeps it, removeSecret=true clears it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "403", description = NOT_SUPER),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/{id}")
    public ApiMessageDto<PlatformWebhookDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody PlatformWebhookForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a platform webhook")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = NOT_SUPER),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Send one test delivery", description = "Payload {event, eventName, module: PLATFORM, "
            + "test: true, companyId: null, sentAt, data}. Returns delivered, the HTTP status and failure TIMEOUT / "
            + "CONNECTION_ERROR; the answer's body is never returned. Redirects are not followed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "URL refused at send time, e.g. unresolvable or "
                    + "private address (SETTINGS_013); stored secret unreadable (SETTINGS_007)"),
            @ApiResponse(responseCode = "403", description = NOT_SUPER),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/{id}/test")
    public ApiMessageDto<TestResult> test(@PathVariable Long id) {
        return ApiMessageDto.ok(service.test(id));
    }
}
