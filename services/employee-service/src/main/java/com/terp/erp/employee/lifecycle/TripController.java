package com.terp.erp.employee.lifecycle;

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
import org.springframework.format.annotation.DateTimeFormat;
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
import com.terp.erp.employee.lifecycle.TripExpenseService.ExpenseForm;
import com.terp.erp.employee.lifecycle.TripExpenseService.TripExpensesDto;
import com.terp.erp.employee.lifecycle.TripService.AdvanceStatusForm;
import com.terp.erp.employee.lifecycle.TripService.ReimbursementStatusForm;
import com.terp.erp.employee.lifecycle.TripService.StatusCounts;
import com.terp.erp.employee.lifecycle.TripService.StatusForm;
import com.terp.erp.employee.lifecycle.TripService.TripDto;
import com.terp.erp.employee.lifecycle.TripService.TripForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;

@Tag(name = "Trips")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/trips")
@SecurityRequirement(name = "bearerAuth")
public class TripController {
    private static final String KEYWORD = "Matches the employee's name, email or employee no., the destination or "
            + "the purpose";
    private static final String CANCELLED = "Cancelled trip (EMP_060)";

    private final TripService service;
    private final TripExpenseService expenseService;

    @Operation(summary = "Trips of the company", description = "startFrom = start date on or after; endTo = end "
            + "date on or before; status = the tab. Amounts in the company currency.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TripDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTo,
            @RequestParam(required = false) EmployeeTrip.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, employeeId, startFrom, endTo, status, pageable)));
    }

    @Operation(summary = "The five cards and the status tabs (Total, Planned, Ongoing, Completed, Cancelled)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTo) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId, startFrom, endTo));
    }

    @Operation(summary = "Trip details", description = "With the financial summary and a signed document link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TripDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a trip (status PLANNED; an advance amount makes the advance REQUESTED)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Employee terminated (EMP_058), end before start "
                    + "(EMP_052), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<TripDto> create(@Valid @RequestBody TripForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a trip", description = "Full replacement of the form; statuses and expenses are kept. "
            + "The advance amount is fixed once the advance is approved.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = CANCELLED + ", advance amount after approval "
                    + "(EMP_062), EMP_052, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such trip (EMP_059) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TripDto> update(@PathVariable Long id, @Valid @RequestBody TripForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change Trip Status (Planned, Ongoing, Completed, Cancelled)",
            description = "Set by hand; once CANCELLED the trip can only be viewed or deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = CANCELLED),
            @ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TripDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Change Advance Status (Requested, Approved, Paid, Reconciled)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No advance (EMP_061), " + CANCELLED),
            @ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/advance-status")
    public ApiMessageDto<TripDto> advanceStatus(@PathVariable Long id, @Valid @RequestBody AdvanceStatusForm form) {
        return ApiMessageDto.ok(service.changeAdvanceStatus(id, form.status()));
    }

    @Operation(summary = "Change Reimbursement Status (Pending, Approved, Paid)",
            description = "PAID fixes the expense lines.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No expenses (EMP_064), " + CANCELLED),
            @ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/reimbursement-status")
    public ApiMessageDto<TripDto> reimbursementStatus(@PathVariable Long id,
                                                      @Valid @RequestBody ReimbursementStatusForm form) {
        return ApiMessageDto.ok(service.changeReimbursementStatus(id, form.status()));
    }

    @Operation(summary = "Delete a trip", description = "Allowed in every status.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Trip Expenses page", description = "Lines by date with the totals and financial summary.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}/expenses")
    public ApiMessageDto<TripExpensesDto> expenses(@PathVariable Long id) {
        return ApiMessageDto.ok(expenseService.list(id));
    }

    @Operation(summary = "Add an expense line", description = "The first line makes the reimbursement PENDING. "
            + "Returns the updated page.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Date outside the trip (EMP_065), type not in the "
                    + "company's list (EMP_050) or inactive (EMP_054), reimbursed (EMP_063), " + CANCELLED
                    + ", file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such trip in your company (EMP_059)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping("/{id}/expenses")
    public ApiMessageDto<TripExpensesDto> addExpense(@PathVariable Long id, @Valid @RequestBody ExpenseForm form) {
        return ApiMessageDto.ok(expenseService.create(id, form));
    }

    @Operation(summary = "Edit an expense line", description = "Full replacement of the line. Returns the page.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_065, EMP_050, EMP_054, EMP_063, EMP_060, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such trip (EMP_059) or line (EMP_066)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/expenses/{expenseId}")
    public ApiMessageDto<TripExpensesDto> updateExpense(@PathVariable Long id, @PathVariable Long expenseId,
                                                        @Valid @RequestBody ExpenseForm form) {
        return ApiMessageDto.ok(expenseService.update(id, expenseId, form));
    }

    @Operation(summary = "Delete an expense line", description = "Removing the last line clears the reimbursement "
            + "status. Returns the page.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Reimbursed (EMP_063), " + CANCELLED),
            @ApiResponse(responseCode = "404", description = "No such trip (EMP_059) or line (EMP_066)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}/expenses/{expenseId}")
    public ApiMessageDto<TripExpensesDto> deleteExpense(@PathVariable Long id, @PathVariable Long expenseId) {
        return ApiMessageDto.ok(expenseService.delete(id, expenseId));
    }
}
