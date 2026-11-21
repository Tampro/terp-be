package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.crm.contract.ContractSettingService.SettingDto;
import com.terp.erp.crm.contract.ContractSettingService.SettingForm;

@Tag(name = "CRM — contract settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/contract-settings")
@SecurityRequirement(name = "bearerAuth")
public class ContractSettingController {
    private final ContractSettingService service;

    @Operation(summary = "Contract numbering", description = "Prefix (default CON) and the next number, e.g. CON0001.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CTR_001)"))
    @PreAuthorize("@PermissionChecker.has('CRM_CONTRACT_MANAGE')")
    @GetMapping
    public ApiMessageDto<SettingDto> get(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Change the number prefix", description = "Full replacement of the setting (prefix only). "
            + "Applies to the next numbers; existing numbers keep theirs and the counter continues.")
    @ApiResponses(@ApiResponse(responseCode = "400",
            description = "Prefix not 1-10 capital letters (COMMON_001), super admin without companyId (CTR_001)"))
    @PreAuthorize("@PermissionChecker.has('CRM_CONTRACT_MANAGE')")
    @PutMapping
    public ApiMessageDto<SettingDto> update(@Valid @RequestBody SettingForm form) {
        return ApiMessageDto.ok(service.update(form));
    }
}
