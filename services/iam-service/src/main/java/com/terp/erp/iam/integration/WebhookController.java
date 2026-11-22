package com.terp.erp.iam.integration;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.integration.WebhookService.EventGroup;
import com.terp.erp.iam.integration.WebhookService.WebhookDto;
import com.terp.erp.iam.integration.WebhookService.WebhookForm;
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

@Tag(name = "Company webhooks", description = "POST of JSON per event. Signed when a secret is set: headers "
        + "X-ERP-Event, X-ERP-Timestamp (epoch s), X-ERP-Signature: sha256=hex(HMAC-SHA256(secret, timestamp + \".\" + body)).")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/webhooks")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NO_COMPANY = "Super admin without companyId (IAM_003)";
    private static final String FORM_ERRORS = "Unknown event (SETTINGS_014), URL refused (SETTINGS_013), secret "
            + "shorter than 16 (SETTINGS_019), no encryption key (SETTINGS_002), validation";

    private final WebhookService service;

    @Operation(summary = "Event catalog, grouped by module")
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/events")
    public ApiMessageDto<List<EventGroup>> events() {
        return ApiMessageDto.ok(service.events());
    }

    @Operation(summary = "Webhooks of the company")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unknown event filter (SETTINGS_014), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<WebhookDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = "Event code filter") @RequestParam(required = false) String eventCode,
            @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, eventCode, pageable)));
    }

    @Operation(summary = "One webhook")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Not found or another company's (SETTINGS_015)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/{id}")
    public ApiMessageDto<WebhookDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a webhook")
    @ApiResponses(@ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping
    public ApiMessageDto<WebhookDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody WebhookForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Update a webhook", description = "Full replacement, except the secret: blank keeps it, "
            + "removeSecret=true clears it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = "Not found or another company's (SETTINGS_015)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<WebhookDto> update(@PathVariable Long id, @Valid @RequestBody WebhookForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a webhook")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Not found or another company's (SETTINGS_015)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Send one test delivery", description = "Payload {event, eventName, module, test: true, "
            + "companyId, sentAt, data}. Returns delivered, the HTTP status (null when no answer) and failure "
            + "TIMEOUT / CONNECTION_ERROR; the answer's body is never returned. Redirects are not followed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "URL refused at send time, e.g. resolves to a private "
                    + "address (SETTINGS_013); stored secret unreadable (SETTINGS_007)"),
            @ApiResponse(responseCode = "404", description = "Not found or another company's (SETTINGS_015)")})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/{id}/test")
    public ApiMessageDto<TestResult> test(@PathVariable Long id) {
        return ApiMessageDto.ok(service.test(id));
    }
}
