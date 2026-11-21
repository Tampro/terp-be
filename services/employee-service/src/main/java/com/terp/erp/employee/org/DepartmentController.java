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
import com.terp.erp.employee.org.DepartmentDtos.DepartmentDto;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentForm;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentOption;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentStatusForm;
import com.terp.erp.employee.org.DepartmentDtos.StatusCounts;

import java.util.List;

@Tag(name = "Departments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org/departments")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final DepartmentService service;

    @Operation(summary = "Departments of the company (Departments page)",
            description = "keyword matches name or description; branchId filters by branch. Sort by name, createdDate, status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DepartmentDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, branchId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same keyword and branch filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, branchId));
    }

    @Operation(summary = "Active departments for dropdowns, optionally of one branch")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.hasAny('ORG_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<DepartmentOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long branchId) {
        return ApiMessageDto.ok(service.options(companyId, branchId));
    }

    @Operation(summary = "Department details with its designation and employee counts")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such department in your company (EMP_003)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DepartmentDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a department to a branch", description = "status omitted = ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken in the branch (EMP_011), inactive branch (EMP_029)"),
            @ApiResponse(responseCode = "404", description = "No such branch in your company (EMP_002)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping
    public ApiMessageDto<DepartmentDto> create(@Valid @RequestBody DepartmentForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a department (full replacement)",
            description = "name, branchId and description are replaced (omitted description = cleared); omitted status "
                    + "keeps the current one. Moving to another branch is refused while a designation or an employee uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011), still in use (EMP_010), inactive "
                    + "branch (EMP_029)"),
            @ApiResponse(responseCode = "404", description = "No such department (EMP_003) or branch (EMP_002)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<DepartmentDto> update(@PathVariable Long id, @Valid @RequestBody DepartmentForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a department",
            description = "An inactive department keeps its employees but cannot be assigned (EMP_030) or get new designations.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such department in your company (EMP_003)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<DepartmentDto> status(@PathVariable Long id, @Valid @RequestBody DepartmentStatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a department", description = "Refused while a designation or an employee uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010)"),
            @ApiResponse(responseCode = "404", description = "No such department in your company (EMP_003)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
