package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.budget.BudgetService.AllocationDto;
import com.terp.erp.accounting.budget.BudgetService.AllocationForm;
import com.terp.erp.accounting.budget.BudgetService.BudgetDto;
import com.terp.erp.accounting.budget.BudgetService.BudgetForm;
import com.terp.erp.accounting.budget.BudgetService.MonitoringDto;
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

import java.util.List;

@Tag(name = "Budgets")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/budgets")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such budget in your company (ACC_090)";
    private static final String STATE = "Wrong status (ACC_091), period closed (ACC_089)";
    private static final String ALLOC = "Account inactive or not in the company (ACC_020), not an expense account "
            + "(ACC_093), allocations over the budget amount (ACC_094), account already allocated (ACC_095), parent or child of an allocated account (ACC_097), "
            + "validation (COMMON)";

    private final BudgetService service;

    @Operation(summary = "Budgets", description = "Filters periodId, status, type.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<BudgetDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long periodId, @RequestParam(required = false) EBudgetStatus status,
            @RequestParam(required = false) EBudgetType type,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, periodId, status, type, pageable)));
    }

    @Operation(summary = "Budget details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<BudgetDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a budget", description = "Starts DRAFT in an ACTIVE period.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Period closed (ACC_089), super admin without "
                    + "companyId (ACC_001), validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = "Period not in your company (ACC_086)")})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PostMapping
    public ApiMessageDto<BudgetDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody BudgetForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a draft budget", description = "Full replacement; DRAFT only; the amount cannot "
            + "fall below the allocations.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE + ", below allocations (ACC_094), "
            + "validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + "; period (ACC_086)")})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<BudgetDto> update(@PathVariable Long id, @Valid @RequestBody BudgetForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a draft budget", description = "Soft delete; DRAFT only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Approve a budget", description = "DRAFT → APPROVED; approvedBy = the caller.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_APPROVE')")
    @PostMapping("/{id}/approve")
    public ApiMessageDto<BudgetDto> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(service.approve(id));
    }

    @Operation(summary = "Activate a budget", description = "APPROVED → ACTIVE.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_APPROVE')")
    @PostMapping("/{id}/activate")
    public ApiMessageDto<BudgetDto> activate(@PathVariable Long id) {
        return ApiMessageDto.ok(service.activate(id));
    }

    @Operation(summary = "Close a budget", description = "ACTIVE → CLOSED.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_APPROVE')")
    @PostMapping("/{id}/close")
    public ApiMessageDto<BudgetDto> close(@PathVariable Long id) {
        return ApiMessageDto.ok(service.close(id));
    }

    @Operation(summary = "Budget monitoring", description = "Per allocation and in total: allocated, spent "
            + "(counted journal debits − credits on the account and its children inside the period), remaining, "
            + "variance % = remaining / allocated × 100 (negative = over budget), overBudget flag.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping("/{id}/monitoring")
    public ApiMessageDto<MonitoringDto> monitoring(@PathVariable Long id) {
        return ApiMessageDto.ok(service.monitoring(id));
    }

    @Operation(summary = "Budget allocations", description = "With spent / remaining computed on read.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('BUDGET_VIEW')")
    @GetMapping("/{id}/allocations")
    public ApiMessageDto<List<AllocationDto>> allocations(@PathVariable Long id) {
        return ApiMessageDto.ok(service.allocations(id));
    }

    @Operation(summary = "Add an allocation", description = "DRAFT, APPROVED or ACTIVE budgets (not CLOSED); expense accounts only; the sum "
            + "of allocations may not exceed the budget amount (unchanged; allocated total is recalculated from the lines).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE + ", " + ALLOC),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PostMapping("/{id}/allocations")
    public ApiMessageDto<AllocationDto> addAllocation(@PathVariable Long id,
                                                      @Valid @RequestBody AllocationForm form) {
        return ApiMessageDto.ok(service.addAllocation(id, form));
    }

    @Operation(summary = "Edit an allocation", description = "Full replacement; DRAFT, APPROVED or ACTIVE budgets (not CLOSED); sum stays within the budget amount.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE + ", " + ALLOC),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", allocation not on the budget "
                    + "(ACC_092)")})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @PutMapping("/{id}/allocations/{allocationId}")
    public ApiMessageDto<AllocationDto> updateAllocation(@PathVariable Long id, @PathVariable Long allocationId,
                                                         @Valid @RequestBody AllocationForm form) {
        return ApiMessageDto.ok(service.updateAllocation(id, allocationId, form));
    }

    @Operation(summary = "Delete an allocation", description = "DRAFT, APPROVED or ACTIVE budgets (not CLOSED).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", allocation not on the budget "
                    + "(ACC_092)")})
    @PreAuthorize("@PermissionChecker.has('BUDGET_MANAGE')")
    @DeleteMapping("/{id}/allocations/{allocationId}")
    public ApiMessageDto<Void> deleteAllocation(@PathVariable Long id, @PathVariable Long allocationId) {
        service.deleteAllocation(id, allocationId);
        return ApiMessageDto.ok(null);
    }
}
