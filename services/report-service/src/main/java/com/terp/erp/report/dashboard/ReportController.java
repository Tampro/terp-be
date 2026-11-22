package com.terp.erp.report.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.report.calendar.CalendarService;
import com.terp.erp.report.calendar.CalendarService.CalendarView;
import com.terp.erp.report.dashboard.DashboardService.CompanyDashboard;
import com.terp.erp.report.dashboard.HrDashboardService.HrDashboard;
import com.terp.erp.report.dashboard.HrDashboardService.MyDashboard;

import java.time.LocalDate;

@Tag(name = "Reports", description = "Company dashboard and calendar")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final DashboardService dashboard;
    private final CalendarService calendar;
    private final HrDashboardService hr;

    @Operation(summary = "Company dashboard",
            description = "Each card is read from the service that owns it with your token: a card you may not see "
                    + "(e.g. payroll without PAYROLL_VIEW) or whose service is down comes back as data = null with "
                    + "the reason in error; the other cards still show.")
    @ApiResponses(@ApiResponse(responseCode = "401", description = "Not signed in (COMMON_003)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/dashboard")
    public ApiMessageDto<CompanyDashboard> dashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = "Year of the hiring and payroll trends; default this year")
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(dashboard.dashboard(companyId, year));
    }

    @Operation(summary = "HR dashboard",
            description = "FR-ERP-DSH-001. Today's attendance (rate, present / absent / on leave) and missing "
                    + "attendance (ATTENDANCE_VIEW), employees per branch (ORG_VIEW) and per department "
                    + "(EMPLOYEE_VIEW), recent promotions and terminations (LIFECYCLE_VIEW), upcoming approved events "
                    + "(EVENT_VIEW), recent leave applications (LEAVE_VIEW), active announcements (ANNOUNCEMENT_VIEW). "
                    + "Each part is read with your token from its service and checked there: a part you may not see "
                    + "or whose service is down is data = null and listed in unavailable with the reason.")
    @ApiResponses(@ApiResponse(responseCode = "401", description = "Not signed in (COMMON_003)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/hr-dashboard")
    public ApiMessageDto<HrDashboard> hrDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(hr.hrDashboard(companyId));
    }

    @Operation(summary = "My dashboard (the signed-in employee)",
            description = "FR-ERP-DSH-001, from the services' self endpoints with your token: myProfile "
                    + "(employees/me, EMPLOYEE_SELF or EMPLOYEE_VIEW), myShift and myAttendanceToday "
                    + "(attendance/me/today, ATTENDANCE_SELF), myWarnings (lifecycle/warnings/my), myAnnouncements "
                    + "(org/announcements/my). myAttendanceThisMonth, myLeaveBalances, myLeaveApplications, myAwards "
                    + "and myComplaints have no self endpoint yet: always data = null with NOT_AVAILABLE. Any part "
                    + "that fails is data = null and listed in unavailable with the reason; the others still show.")
    @ApiResponses(@ApiResponse(responseCode = "401", description = "Not signed in (COMMON_003)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-dashboard")
    public ApiMessageDto<MyDashboard> myDashboard() {
        return ApiMessageDto.ok(hr.myDashboard());
    }

    @Operation(summary = "Unified calendar (month / week / day)",
            description = "Meetings, holidays, leaves, birthdays, sales and purchase invoices (due date), company "
                    + "events, deal and lead tasks, contract ends, project tasks (due date), Zoom meetings and "
                    + "interviews. At most 62 days (a month view with the days of the weeks around it). kinds = the "
                    + "filter (comma list, default all): only those services are called. Kinds you may not see are "
                    + "left out and listed in unavailable.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Range too long (REP_001), to before from (REP_002), "
                    + "unknown kind (COMMON_001)"),
            @ApiResponse(responseCode = "401", description = "Not signed in (COMMON_003)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/calendar")
    public ApiMessageDto<CalendarView> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Kinds to show, comma separated (e.g. MEETING,SALES_INVOICE); default all")
            @RequestParam(required = false) java.util.List<CalendarService.EKind> kinds) {
        return ApiMessageDto.ok(calendar.calendar(companyId, from, to, kinds));
    }
}
