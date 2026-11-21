package com.terp.erp.employee.training;

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
import com.terp.erp.employee.training.TrainingSupport.Ref;
import com.terp.erp.employee.training.TrainingTypeService.DepartmentsForm;
import com.terp.erp.employee.training.TrainingTypeService.TypeDto;
import com.terp.erp.employee.training.TrainingTypeService.TypeForm;

import java.util.List;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Training types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/types")
@SecurityRequirement(name = "bearerAuth")
public class TrainingTypeController {
    private static final String NOT_FOUND = "No such training type in your company (EMP_080)";

    private final TrainingTypeService service;

    @Operation(summary = "Training types of the company", description = "keyword matches the name or description; "
            + "branchId = the type's branch; departmentId = types assigned to that department.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, branchId, departmentId,
                pageable)));
    }

    @Operation(summary = "Training types for dropdowns, by name")
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Training type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a training type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), branch not in the company "
            + "(EMP_002), super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                         Long companyId, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a training type", description = "Full replacement of name, description and branch; "
            + "assigned departments are kept and must belong to a new branch.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_011, EMP_002, departments outside the new branch "
                    + "(EMP_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Assign Departments", description = "Replaces the departments; they must be of the "
            + "company and of the type's branch when one is set.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Department not in the company / branch (EMP_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}/departments")
    public ApiMessageDto<TypeDto> departments(@PathVariable Long id, @Valid @RequestBody DepartmentsForm form) {
        return ApiMessageDto.ok(service.assignDepartments(id, form.departmentIds()));
    }

    @Operation(summary = "Delete a training type")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Has programs (EMP_010)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
