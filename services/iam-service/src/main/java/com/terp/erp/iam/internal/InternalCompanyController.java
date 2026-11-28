package com.terp.erp.iam.internal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.repository.CompanyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (SecurityConfig internalChain, erp.iam.internal-client-ids): whether a company may serve
 * its public pages. A deleted or unknown company answers active=false rather than 404, so the caller has one rule.
 */
@Tag(name = "Internal")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/companies")
@SecurityRequirement(name = "bearerAuth")
public class InternalCompanyController {
    private final CompanyRepository companies;

    public record CompanyStatus(String companyId, boolean active) {
    }

    @Operation(summary = "Is the company active? (store-service public storefront)",
            description = "active = exists, not deleted and status ACTIVE; a suspended, deleted or unknown company is false.")
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "No or invalid token (COMMON_003)"),
            @ApiResponse(responseCode = "403", description = "Token not of an allowed service account (COMMON_004)")})
    @Transactional(readOnly = true)
    @GetMapping("/{id}/status")
    public ApiMessageDto<CompanyStatus> status(@PathVariable Long id) {
        boolean active = companies.findByIdAndDeletedFalse(id).map(c -> c.getStatus() == ECompanyStatus.ACTIVE)
                .orElse(false);
        return ApiMessageDto.ok(new CompanyStatus(String.valueOf(id), active));
    }
}
