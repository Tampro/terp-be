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
import com.terp.erp.report.dashboard.ModuleDashboardService.ModuleDashboard;

import java.time.LocalDate;

@Tag(name = "Reports", description = "Company dashboard and calendar")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String YEAR = "Year of the monthly figures; default this year";
    private static final String PARTS = " Each part is read with your token from the service that owns it and checked "
            + "there (permission, company, own scope): a part you may not see or whose service is down is "
            + "data = null and listed in unavailable with the reason; the others still show.";
    private static final String ZONE = " Days, months and hours are taken in the company's time zone (IAM "
            + "settings, timezoneSource = COMPANY; Asia/Ho_Chi_Minh with timezoneSource = DEFAULT when IAM does not "
            + "answer).";
    private static final String BAD_YEAR = "year out of 2000-2100 (COMMON_001)";
    private static final String NO_LOGIN = "Not signed in (COMMON_003)";

    private final DashboardService dashboard;
    private final CalendarService calendar;
    private final HrDashboardService hr;
    private final ModuleDashboardService modules;

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
                    + "(org/announcements/my), myLeaveBalances and myLeaveApplications {pendingApplications, "
                    + "recentApplications} (attendance/me/leave, ATTENDANCE_SELF), myAwards (lifecycle/awards/my), "
                    + "myInterviews (recruitment/interviews/my, where I am on the panel), myDeals {deals, recentDeals} "
                    + "(crm/dashboard?own=true), myTickets {total, open, closed, byStatus} "
                    + "(support/tickets/stats?own=true). myAttendanceThisMonth and myComplaints have no self "
                    + "endpoint yet: always data = null with NOT_AVAILABLE. Any part "
                    + "that fails is data = null and listed in unavailable with the reason; the others still show.")
    @ApiResponses(@ApiResponse(responseCode = "401", description = "Not signed in (COMMON_003)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-dashboard")
    public ApiMessageDto<MyDashboard> myDashboard() {
        return ApiMessageDto.ok(hr.myDashboard());
    }

    @Operation(summary = "Project dashboard", description = "Manual 5.1. parts.stats = project-service "
            + "/projects/stats: totals, task completion, overdue projects, active bugs, team, clients, monthly tasks "
            + "created vs completed, status / priority split, team performance. PROJECT_VIEW_OWN sees own projects "
            + "only (scope = OWN)." + PARTS + ZONE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "Neither PROJECT_VIEW nor PROJECT_VIEW_OWN (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.hasAny('PROJECT_VIEW','PROJECT_VIEW_OWN')")
    @GetMapping("/project-dashboard")
    public ApiMessageDto<ModuleDashboard> projectDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.project(companyId, year));
    }

    @Operation(summary = "Account dashboard", description = "Manual 5.2. parts.stats = accounting "
            + "/accounting/reports/dashboard: revenue, expense, customer / vendor payments and net cash flow of the "
            + "year, client / vendor counts, monthly in / out, 5 recent revenues and expenses." + PARTS)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "No ACCOUNTING_REPORT_VIEW (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.has('ACCOUNTING_REPORT_VIEW')")
    @GetMapping("/account-dashboard")
    public ApiMessageDto<ModuleDashboard> accountDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.account(companyId, year));
    }

    @Operation(summary = "POS dashboard", description = "Manual 5.5. parts.sales = commerce "
            + "/pos/reports/dashboard (today's sales, year totals, average transaction, monthly orders, 7x24 "
            + "hour x day-of-week heat map, top products); parts.stock = inventory /inventory/stock/summary "
            + "(products, out of stock, out of stock per warehouse)." + PARTS + ZONE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "No POS_REPORT (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.has('POS_REPORT')")
    @GetMapping("/pos-dashboard")
    public ApiMessageDto<ModuleDashboard> posDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.pos(companyId, year));
    }

    @Operation(summary = "CRM dashboard", description = "Manual 5.6. parts.stats = crm /crm/dashboard: deals, "
            + "leads, users, clients, calls, 10 upcoming lead tasks, deals by stage, 5 recent deals and leads. "
            + "CRM_VIEW_OWN sees own leads / deals only (scope = OWN)." + PARTS + ZONE)
    @ApiResponses({@ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "Neither CRM_VIEW nor CRM_VIEW_OWN (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.hasAny('CRM_VIEW','CRM_VIEW_OWN')")
    @GetMapping("/crm-dashboard")
    public ApiMessageDto<ModuleDashboard> crmDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(modules.crm(companyId));
    }

    @Operation(summary = "Support dashboard", description = "Manual 5.7. parts.stats = support "
            + "/support/tickets/stats: total / open / closed / today tickets, average first response (minutes), "
            + "categories, KB articles, FAQs, monthly created vs closed, status split. SUPPORT_TICKET_VIEW_OWN sees "
            + "own tickets only (scope = OWN)." + PARTS + ZONE)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "Neither SUPPORT_TICKET_VIEW nor _OWN (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')")
    @GetMapping("/support-dashboard")
    public ApiMessageDto<ModuleDashboard> supportDashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.support(companyId, year));
    }

    @Operation(summary = "Client dashboard (portal)", description = "Manual 5 role view Client, all of my "
            + "customer only: parts.payments = accounting /portal/client/payments/summary (totalSettled, monthly "
            + "trend, credit notes), parts.deals = {count} of crm portal deals, parts.openTickets = {count} of my "
            + "IN_PROGRESS + ON_HOLD portal tickets (unavailable if either count fails)." + PARTS)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "No PORTAL_CLIENT (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/client-dashboard")
    public ApiMessageDto<ModuleDashboard> clientDashboard(
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.client(year));
    }

    @Operation(summary = "Vendor dashboard (portal)", description = "Manual 5 role view Vendor, all of my vendor "
            + "only: parts.payments = accounting /portal/vendor/payments/summary (totalSettled = received, monthly "
            + "disbursement trend, debit notes), parts.purchaseInvoices = {count}; parts.requests is always "
            + "NOT_AVAILABLE (the manual does not say what a request is)." + PARTS)
    @ApiResponses({@ApiResponse(responseCode = "400", description = BAD_YEAR),
            @ApiResponse(responseCode = "401", description = NO_LOGIN),
            @ApiResponse(responseCode = "403", description = "No PORTAL_VENDOR (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
    @GetMapping("/vendor-dashboard")
    public ApiMessageDto<ModuleDashboard> vendorDashboard(
            @Parameter(description = YEAR) @RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return ApiMessageDto.ok(modules.vendor(year));
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
