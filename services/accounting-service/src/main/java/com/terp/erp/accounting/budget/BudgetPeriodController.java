package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.budget.BudgetPeriodService.PeriodDto;
import com.terp.erp.accounting.budget.BudgetPeriodService.PeriodForm;
import com.terp.erp.accounting.budget.BudgetService.PeriodMonitoringDto;
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

@Tag(name = "Budget periods")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/budget-periods")
@SecurityRequirement(name = "bearerAuth")
public class BudgetPeriodController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such budget period in your company (ACC_086)";
    private static final String INVALID = "End not after start or longer than 366 days (ACC_087), validation (COMMON)";

    private final BudgetPeriodService service;
    private final BudgetService budgets;

    @Operation(summary = "Budget periods", description = "Filters status, financialYear.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PeriodDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) EBudgetPeriodStatus status,
            @RequestParam(required = false) Integer financialYear,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, status, financialYear, pageable)));
    }

    @Operation(summary = "Budget period details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<PeriodDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a budget period", description = "Starts ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = INVALID
            + ", super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PostMapping
    public ApiMessageDto<PeriodDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody PeriodForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a budget period", description = "Full replacement; ACTIVE periods only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = INVALID + ", period closed (ACC_089)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<PeriodDto> update(@PathVariable Long id, @Valid @RequestBody PeriodForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a budget period", description = "Soft delete; ACTIVE and without budgets.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Period closed (ACC_089), has budgets "
            + "(ACC_096)"), @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Close a budget period", description = "ACTIVE → CLOSED, only after the end date; its "
            + "budgets become read-only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "End date not passed (ACC_088), already "
            + "closed (ACC_089)"), @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PostMapping("/{id}/close")
    public ApiMessageDto<PeriodDto> close(@PathVariable Long id) {
        return ApiMessageDto.ok(service.close(id));
    }

    @Operation(summary = "Budget monitoring of a period", description = "Every live budget of the period with its "
            + "allocations: allocated, spent (counted journal lines in the period), remaining, variance % "
            + "(remaining / allocated × 100), over-budget flags; totals for the period.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping("/{id}/monitoring")
    public ApiMessageDto<PeriodMonitoringDto> monitoring(@PathVariable Long id) {
        return ApiMessageDto.ok(budgets.periodMonitoring(id));
    }
}
