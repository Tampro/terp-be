package com.terp.erp.accounting.posting;

import com.terp.erp.accounting.posting.DocumentPostingService.PostingDto;
import com.terp.erp.accounting.posting.DocumentPostingService.PostingForm;
import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Journal postings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/journal-postings")
@SecurityRequirement(name = "bearerAuth")
public class DocumentPostingController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final DocumentPostingService service;

    @Operation(summary = "Journal a commerce document", description = "Called by commerce-service when a sales / "
            + "purchase invoice is posted or a return completed. Amounts come by role (goods, service, tax, cost); the "
            + "accounts come from the system account mapping. SALES_* needs SALES_POST, PURCHASE_* PURCHASE_POST. "
            + "Idempotent per (company, sourceType, sourceId): a repeat returns the first entry. validateOnly = true "
            + "checks mapping, closed year and balance and writes nothing (commerce calls it before moving stock).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unmapped / inactive account (ACC_017), all amounts "
                    + "zero (ACC_019), date in a closed year (ACC_070), super admin without companyId (ACC_001), "
                    + "invalid field (COMMON)"),
            @ApiResponse(responseCode = "403", description = "Missing the permission of this sourceType (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.hasAny('SALES_POST','PURCHASE_POST')")
    @PostMapping
    public ApiMessageDto<PostingDto> post(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody PostingForm form) {
        return ApiMessageDto.ok(service.post(companyId, form));
    }
}
