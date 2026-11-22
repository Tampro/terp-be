package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.coa.CategoryService.CategoryDto;
import com.terp.erp.accounting.coa.CategoryService.CategoryForm;
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

import static com.terp.erp.accounting.coa.ECategoryKind.EXPENSE;
import static com.terp.erp.accounting.coa.ECategoryKind.REVENUE;

@Tag(name = "Revenue and expense categories")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('COA_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('COA_MANAGE')";
    private static final String INVALID = "Code already used (ACC_016), GL account inactive or not in the company "
            + "(ACC_020), invalid field (COMMON)";
    private static final String REV_NOT_FOUND = "No such revenue category in your company (ACC_014)";
    private static final String EXP_NOT_FOUND = "No such expense category in your company (ACC_015)";

    private final CategoryService service;

    @Operation(summary = "Revenue categories", description = "enabled filters by status (omit for all).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/revenue-categories")
    public ApiMessageDto<ResponseListDto<CategoryDto>> revenueList(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Boolean enabled,
            @ParameterObject @PageableDefault(size = 50, sort = "code") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(REVENUE, companyId, enabled, pageable)));
    }

    @Operation(summary = "Revenue category details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = REV_NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/revenue-categories/{id}")
    public ApiMessageDto<CategoryDto> revenueGet(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(REVENUE, id));
    }

    @Operation(summary = "Add a revenue category")
    @ApiResponses(@ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId "
            + "(ACC_001)"))
    @PreAuthorize(MANAGE)
    @PostMapping("/revenue-categories")
    public ApiMessageDto<CategoryDto> revenueCreate(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.create(REVENUE, companyId, form));
    }

    @Operation(summary = "Edit a revenue category", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = INVALID),
            @ApiResponse(responseCode = "404", description = REV_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/revenue-categories/{id}")
    public ApiMessageDto<CategoryDto> revenueUpdate(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.update(REVENUE, id, form));
    }

    @Operation(summary = "Delete a revenue category", description = "Refused while revenues use it.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "In use (ACC_033)"),
            @ApiResponse(responseCode = "404", description = REV_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/revenue-categories/{id}")
    public ApiMessageDto<Void> revenueDelete(@PathVariable Long id) {
        service.delete(REVENUE, id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Expense categories", description = "enabled filters by status (omit for all).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/expense-categories")
    public ApiMessageDto<ResponseListDto<CategoryDto>> expenseList(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Boolean enabled,
            @ParameterObject @PageableDefault(size = 50, sort = "code", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(EXPENSE, companyId, enabled, pageable)));
    }

    @Operation(summary = "Expense category details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = EXP_NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/expense-categories/{id}")
    public ApiMessageDto<CategoryDto> expenseGet(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(EXPENSE, id));
    }

    @Operation(summary = "Add an expense category")
    @ApiResponses(@ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId "
            + "(ACC_001)"))
    @PreAuthorize(MANAGE)
    @PostMapping("/expense-categories")
    public ApiMessageDto<CategoryDto> expenseCreate(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.create(EXPENSE, companyId, form));
    }

    @Operation(summary = "Edit an expense category", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = INVALID),
            @ApiResponse(responseCode = "404", description = EXP_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/expense-categories/{id}")
    public ApiMessageDto<CategoryDto> expenseUpdate(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.update(EXPENSE, id, form));
    }

    @Operation(summary = "Delete an expense category", description = "Refused while expenses use it.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "In use (ACC_033)"),
            @ApiResponse(responseCode = "404", description = EXP_NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/expense-categories/{id}")
    public ApiMessageDto<Void> expenseDelete(@PathVariable Long id) {
        service.delete(EXPENSE, id);
        return ApiMessageDto.ok(null);
    }
}
