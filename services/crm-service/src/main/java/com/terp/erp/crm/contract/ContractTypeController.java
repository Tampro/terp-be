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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.crm.contract.ContractTypeService.TypeDto;
import com.terp.erp.crm.contract.ContractTypeService.TypeForm;

import java.util.List;

@Tag(name = "CRM — contract types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/contract-types")
@SecurityRequirement(name = "bearerAuth")
public class ContractTypeController {
    private final ContractTypeService service;

    @Operation(summary = "Contract types of the company", description = "Sorted by name; inactive ones included.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CTR_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('CRM_CONTRACT_VIEW', 'CRM_CONTRACT_MANAGE')")
    @GetMapping
    public ApiMessageDto<List<TypeDto>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.list(companyId));
    }

    @Operation(summary = "Create a contract type")
    @ApiResponses(@ApiResponse(responseCode = "400",
            description = "Name already exists (CTR_004), super admin without companyId (CTR_001), validation (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('CRM_CONTRACT_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a contract type", description = "Full replacement of name and active; companyId is ignored.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Name already exists (CTR_004), validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "No such type in your company (CTR_003)")})
    @PreAuthorize("@PermissionChecker.has('CRM_CONTRACT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a contract type", description = "Soft delete; refused while a contract uses it.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Used by contracts (CTR_005)"),
            @ApiResponse(responseCode = "404", description = "No such type in your company (CTR_003)")})
    @PreAuthorize("@PermissionChecker.has('CRM_CONTRACT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
