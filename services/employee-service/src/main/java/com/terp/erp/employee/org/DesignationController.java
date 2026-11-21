package com.terp.erp.employee.org;

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
import com.terp.erp.employee.org.DesignationDtos.DesignationDto;
import com.terp.erp.employee.org.DesignationDtos.DesignationForm;
import com.terp.erp.employee.org.DesignationDtos.DesignationOption;
import com.terp.erp.employee.org.DesignationDtos.DesignationStatusForm;

import java.util.List;

@Tag(name = "Designations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org/designations")
@SecurityRequirement(name = "bearerAuth")
public class DesignationController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final DesignationService service;

    @Operation(summary = "Designations of the company (Designations page)",
            description = "keyword matches name or description; departmentId and status filter. Each row carries its "
                    + "department and branch. Sort by name, createdDate, status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DesignationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, departmentId, status, pageable)));
    }

    @Operation(summary = "Active designations for dropdowns, optionally of one department")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.hasAny('ORG_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<DesignationOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long departmentId) {
        return ApiMessageDto.ok(service.options(companyId, departmentId));
    }

    @Operation(summary = "Designation details with its department, branch and employee count")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such designation in your company (EMP_004)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DesignationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a designation to a department", description = "status omitted = ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken in the department (EMP_011), inactive "
                    + "department (EMP_030)"),
            @ApiResponse(responseCode = "404", description = "No such department in your company (EMP_003)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping
    public ApiMessageDto<DesignationDto> create(@Valid @RequestBody DesignationForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a designation (full replacement)",
            description = "name, departmentId and description are replaced (omitted description = cleared); omitted "
                    + "status keeps the current one. Moving to another department is refused while an employee uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011), still in use (EMP_010), inactive "
                    + "department (EMP_030)"),
            @ApiResponse(responseCode = "404", description = "No such designation (EMP_004) or department (EMP_003)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<DesignationDto> update(@PathVariable Long id, @Valid @RequestBody DesignationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a designation",
            description = "An inactive designation keeps its employees but cannot be assigned (EMP_031).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such designation in your company (EMP_004)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<DesignationDto> status(@PathVariable Long id, @Valid @RequestBody DesignationStatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a designation", description = "Refused while an employee uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010)"),
            @ApiResponse(responseCode = "404", description = "No such designation in your company (EMP_004)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
