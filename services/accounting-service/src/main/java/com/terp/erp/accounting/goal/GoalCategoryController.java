package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.goal.GoalCategoryService.CategoryDto;
import com.terp.erp.accounting.goal.GoalCategoryService.CategoryForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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

@Tag(name = "Goal categories")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/goal-categories")
@SecurityRequirement(name = "bearerAuth")
public class GoalCategoryController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such goal category in your company (ACC_078)";

    private final GoalCategoryService service;

    @Operation(summary = "Goal categories")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CategoryDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, active, pageable)));
    }

    @Operation(summary = "Goal category details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CategoryDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a goal category", description = "Code is upper-cased, unique per company.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code used (ACC_079), super admin without "
            + "companyId (ACC_001), validation (COMMON)"))
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping
    public ApiMessageDto<CategoryDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a goal category", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Code used (ACC_079), validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CategoryDto> update(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a goal category", description = "Soft delete; goals keep their categoryId.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
