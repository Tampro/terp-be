package com.terp.erp.attendance.leave;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.leave.LeaveAppDtos.BalancePage;
import com.terp.erp.attendance.leave.LeaveAppDtos.SyncDto;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

@Tag(name = "Leave Balances")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/leave/balances")
@SecurityRequirement(name = "bearerAuth")
public class LeaveBalanceController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final LeaveBalanceService service;

    @Operation(summary = "Leave balance cards of a year: per employee, per leave type",
            description = "Total = allocated + carried forward + adjustment; Available = Total - used. lastSyncedAt = "
                    + "the last Sync of that year. Employees are paged like the employee list (keyword = employee "
                    + "search). Needs EMPLOYEE_VIEW too.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping
    public ApiMessageDto<BalancePage> cards(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiMessageDto.ok(service.cards(companyId, year, employeeId, keyword, page, size));
    }

    @Operation(summary = "Sync / Re-sync the balances of a year",
            description = "One balance per (employee not terminated, active leave type): allocated = the type's days "
                    + "per year, used = approved leave days of the year, carried forward = what was left last year up "
                    + "to the policy's limit. Run it after adding an employee, a leave type or a policy.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PostMapping("/sync")
    public ApiMessageDto<SyncDto> sync(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                       @RequestParam @Min(2000) @Max(2100) int year) {
        return ApiMessageDto.ok(service.sync(companyId, year));
    }

    @Operation(summary = "Leave Balance Sync History", description = "keyword matches Synced By; year filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/syncs")
    public ApiMessageDto<ResponseListDto<SyncDto>> history(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "syncedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.history(companyId, year, keyword, pageable)));
    }
}
