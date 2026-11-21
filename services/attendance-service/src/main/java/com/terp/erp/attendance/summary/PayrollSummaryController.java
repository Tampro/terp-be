package com.terp.erp.attendance.summary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.summary.PayrollSummaryService.Summary;
import com.terp.erp.common.ApiMessageDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;


@Tag(name = "Payroll summary")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/payroll-summary")
@SecurityRequirement(name = "bearerAuth")
public class PayrollSummaryController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final PayrollSummaryService service;

    @Operation(summary = "Attendance of a pay period per employee (used by payroll-service)",
            description = "Working days = days that are neither a weekly day off nor a holiday of the employee's "
                    + "branch. Present: PRESENT 1, HALF_DAY 0.5. Approved leave on working days is split into paid "
                    + "and unpaid by the leave type. LOP: ABSENT 1, HALF_DAY 0.5 on working days without leave; a "
                    + "day without a record is not LOP. Overtime = hours and amount of the records. Employees not "
                    + "in the company are left out.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or longer than 62 days (ATT_051), "
            + "more than 100 employees (ATT_052), super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<List<Summary>> summary(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Employee ids, at most 100") @RequestParam Set<Long> employeeIds) {
        return ApiMessageDto.ok(service.summary(companyId, from, to, employeeIds));
    }
}
