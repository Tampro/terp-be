package com.terp.erp.iam.ai;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.ai.AiDtos.ComposeForm;
import com.terp.erp.iam.ai.AiDtos.ComposeResult;
import com.terp.erp.iam.ai.AiDtos.UsageView;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Under /api/v1/iam/** because the gateway already routes that prefix to IAM (no gateway change needed). */
@Tag(name = "AI assistant", description = "Drafts texts with the company's AI provider (ERP manual 19).")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/ai")
@SecurityRequirement(name = "bearerAuth")
public class AiAssistantController {
    private final AiAssistantService service;

    @Operation(summary = "Draft a text with the AI assistant",
            description = "Sends the context and instructions to the AI provider configured for the company "
                    + "(Settings > Integrations > AI assistant), with a fixed system prompt per purpose. Nothing is "
                    + "sent unless the caller is a company user with AI_ASSISTANT_USE, the plan includes AI "
                    + "integration and the integration is enabled. Output is capped (erp.ai.max-output-tokens). "
                    + "The text is not stored; a usage row records sizes only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Generated text, provider and model"),
            @ApiResponse(responseCode = "400", description = "Integration missing or off (AI_003), context or "
                    + "instructions too long (AI_004 / validation), key rejected by the provider (AI_005), request "
                    + "refused by the provider (AI_009), invalid model name (AI_010), host refused (SETTINGS_013)"),
            @ApiResponse(responseCode = "403", description = "Super admin (AI_001), plan without AI integration "
                    + "(AI_002), missing AI_ASSISTANT_USE"),
            @ApiResponse(responseCode = "429", description = "Company limit per minute or provider limit (AI_006)"),
            @ApiResponse(responseCode = "502", description = "Unreadable provider answer (AI_008)"),
            @ApiResponse(responseCode = "503", description = "Provider down or timed out (AI_007)")})
    @PreAuthorize("@PermissionChecker.has('AI_ASSISTANT_USE')")
    @PostMapping("/compose")
    public ApiMessageDto<ComposeResult> compose(@Valid @RequestBody ComposeForm form) {
        return ApiMessageDto.ok(service.compose(form));
    }

    @Operation(summary = "AI assistant usage log of the company",
            description = "One row per compose attempt that passed the pre-checks: purpose, provider, model, status, "
                    + "sizes and latency. Never any text.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/usage")
    public ApiMessageDto<ResponseListDto<UsageView>> usage(
            @Parameter(description = "Super admin only: the company to read. Ignored for company users.")
            @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.usage(companyId, pageable)));
    }
}
