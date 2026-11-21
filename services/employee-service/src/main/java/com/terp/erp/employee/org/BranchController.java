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
import com.terp.erp.employee.org.BranchDtos.BranchDto;
import com.terp.erp.employee.org.BranchDtos.BranchForm;
import com.terp.erp.employee.org.BranchDtos.BranchOption;
import com.terp.erp.employee.org.BranchDtos.BranchStatusForm;

import java.util.List;

@Tag(name = "Branches")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org/branches")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final BranchService service;

    @Operation(summary = "Branches of the company (Branches page)",
            description = "keyword matches name, email or phone. Sort by name, createdDate, status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<BranchDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EOrgStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Active branches for dropdowns (Add Employee, Add Department)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.hasAny('ORG_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<BranchOption>> options(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Branch details with its department and employee counts")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such branch in your company (EMP_002)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<BranchDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a branch", description = "status omitted = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), no company (EMP_019), "
            + "invalid fields (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping
    public ApiMessageDto<BranchDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody BranchForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a branch (full replacement)",
            description = "Every field is replaced; omitted optional fields are cleared. Omitted status keeps the current one.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011)"),
            @ApiResponse(responseCode = "404", description = "No such branch in your company (EMP_002)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<BranchDto> update(@PathVariable Long id, @Valid @RequestBody BranchForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a branch",
            description = "An inactive branch keeps its employees but cannot be assigned to an employee (EMP_029).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such branch in your company (EMP_002)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<BranchDto> status(@PathVariable Long id, @Valid @RequestBody BranchStatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a branch", description = "Refused while a department or an employee uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010)"),
            @ApiResponse(responseCode = "404", description = "No such branch in your company (EMP_002)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
