package com.terp.erp.attendance.record;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.leave.LeaveDashboardService;
import com.terp.erp.attendance.leave.LeaveDashboardService.LeaveDashboardView;
import com.terp.erp.attendance.record.AttendanceDashboardService.AttendanceDashboard;
import com.terp.erp.common.ApiMessageDto;

/** Attendance and leave parts of the company dashboard (docs/analysis/06 §5.1), for the report service and the FE. */
@Tag(name = "Attendance Dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceDashboardController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final AttendanceDashboardService attendance;
    private final LeaveDashboardService leave;

    @Operation(summary = "Attendance figures of the dashboard",
            description = "presentToday, attendanceRateThisMonth (%, present + half days / employees × working days "
                    + "so far; null without data) and last7Days (present incl. half days, on leave, absent records). "
                    + "Days without a record are not counted as absent.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/dashboard")
    public ApiMessageDto<AttendanceDashboard> dashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(attendance.dashboard(companyId));
    }

    @Operation(summary = "Leave figures of the dashboard",
            description = "pending applications, todayLeaves (approved leaves covering today) and month (this month's "
                    + "applications by status, by start date).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/leave/dashboard")
    public ApiMessageDto<LeaveDashboardView> leaveDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(leave.dashboard(companyId));
    }
}
