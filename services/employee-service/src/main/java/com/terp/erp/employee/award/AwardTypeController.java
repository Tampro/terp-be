package com.terp.erp.employee.award;

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
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeDto;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeForm;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeOption;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeStatusForm;
import com.terp.erp.employee.org.EOrgStatus;

import java.util.List;

@Tag(name = "Award Types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org/award-types")
@SecurityRequirement(name = "bearerAuth")
public class AwardTypeController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final AwardTypeService service;

    @Operation(summary = "Award types of the company",
            description = "keyword matches name or description; status = ACTIVE or INACTIVE (All Statuses = omitted). "
                    + "Sort by name, status, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<AwardTypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Active award types for dropdowns")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.hasAny('ORG_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<AwardTypeOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Award type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such award type in your company (EMP_042)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<AwardTypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an award type", description = "status omitted = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping
    public ApiMessageDto<AwardTypeDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody AwardTypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an award type (full replacement)",
            description = "name, description and icon are replaced (omitted = cleared); omitted status keeps the current one.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011)"),
            @ApiResponse(responseCode = "404", description = "No such award type in your company (EMP_042)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<AwardTypeDto> update(@PathVariable Long id, @Valid @RequestBody AwardTypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate an award type")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such award type in your company (EMP_042)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<AwardTypeDto> status(@PathVariable Long id, @Valid @RequestBody AwardTypeStatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an award type")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Used by an award (EMP_010)"),
            @ApiResponse(responseCode = "404", description = "No such award type in your company (EMP_042)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
