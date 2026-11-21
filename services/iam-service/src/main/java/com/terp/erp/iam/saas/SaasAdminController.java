package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.Operation;
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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.saas.SaasDtos.OrderDecisionForm;
import com.terp.erp.iam.saas.SaasDtos.OrderDto;
import com.terp.erp.iam.saas.SaasDtos.PlanDto;
import com.terp.erp.iam.saas.SaasDtos.PlanForm;
import com.terp.erp.iam.saas.SaasDtos.SubscriptionAssignForm;
import com.terp.erp.iam.saas.SaasDtos.SubscriptionDto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "SaaS — administration (super admin)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saas/admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class SaasAdminController {
    private final CatalogService catalog;
    private final OrderService orderService;
    private final PlanRequestService planRequestService;
    private final AdminDashboardService dashboard;

    @Operation(summary = "Super Admin Dashboard", description = "Totals, recently registered companies, top plans by "
            + "approved revenue, and new companies / revenue per month of year (default: the current year, UTC). "
            + "Revenue = final price of APPROVED plan orders by approval date.")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "Not a super admin"))
    @GetMapping("/dashboard")
    public ApiMessageDto<AdminDashboardService.AdminDashboard> dashboard(
            @RequestParam(required = false) @jakarta.validation.constraints.Min(2000)
            @jakarta.validation.constraints.Max(2100) Integer year) {
        return ApiMessageDto.ok(dashboard.dashboard(year));
    }

    @Operation(summary = "All plans (enabled or not)")
    @GetMapping("/plans")
    public ApiMessageDto<List<PlanDto>> plans() {
        return ApiMessageDto.ok(catalog.allPlans());
    }

    @Operation(summary = "Create a plan", description = "defaultPlan = true moves the default flag to this plan.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code taken (SAAS_002)"))
    @PostMapping("/plans")
    public ApiMessageDto<PlanDto> createPlan(@Valid @RequestBody PlanForm form) {
        return ApiMessageDto.ok(catalog.createPlan(form));
    }

    @Operation(summary = "Update a plan (full replacement)",
            description = "PUT replaces every field: omitted limits become unlimited. New limits apply to every "
                    + "company on the plan.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code taken (SAAS_002)"),
            @ApiResponse(responseCode = "404", description = "No such plan (SAAS_001)")})
    @PutMapping("/plans/{id}")
    public ApiMessageDto<PlanDto> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanForm form) {
        return ApiMessageDto.ok(catalog.updatePlan(id, form));
    }

    @Operation(summary = "Delete a plan (soft)", description = "Companies on it fall back to the default plan or EXPIRED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Default plan (SAAS_013)"),
            @ApiResponse(responseCode = "404", description = "No such plan (SAAS_001)")})
    @DeleteMapping("/plans/{id}")
    public ApiMessageDto<Void> deletePlan(@PathVariable Long id) {
        catalog.deletePlan(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Plan Orders page", description = "keyword matches order number or company name/code/email; "
            + "dateFrom/dateTo are inclusive UTC order days; status = tab (omitted = All). Rows carry company and plan names.")
    @GetMapping("/orders")
    public ApiMessageDto<ResponseListDto<SaasDtos.AdminOrderDto>> orders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) EOrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(orderService.all(keyword, dateFrom, dateTo, status, pageable)));
    }

    @Operation(summary = "Counts for the order status tabs (same filters as the list)")
    @GetMapping("/orders/status-counts")
    public ApiMessageDto<SaasDtos.StatusCountsDto> orderCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiMessageDto.ok(orderService.counts(keyword, dateFrom, dateTo));
    }

    @Operation(summary = "View an order")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such order (SAAS_008)"))
    @GetMapping("/orders/{id}")
    public ApiMessageDto<SaasDtos.AdminOrderDto> order(@PathVariable Long id) {
        return ApiMessageDto.ok(orderService.adminGet(id));
    }

    @Operation(summary = "Plan Requests page", description = "keyword matches company name/code/email or plan name; "
            + "status = tab (omitted = All).")
    @GetMapping("/plan-requests")
    public ApiMessageDto<ResponseListDto<SaasDtos.PlanRequestDto>> planRequests(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EPlanRequestStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(planRequestService.search(keyword, status, pageable)));
    }

    @Operation(summary = "Counts for the plan request status tabs")
    @GetMapping("/plan-requests/status-counts")
    public ApiMessageDto<SaasDtos.StatusCountsDto> planRequestCounts(@RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(planRequestService.counts(keyword));
    }

    @Operation(summary = "Approve a plan request", description = "Activates the plan for one billing cycle (extends the "
            + "running period of the same plan) and records an APPROVED MANUAL order.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_017), no yearly price (SAAS_003)"),
            @ApiResponse(responseCode = "404", description = "No such request (SAAS_016), plan deleted (SAAS_001)")})
    @PostMapping("/plan-requests/{id}/approve")
    public ApiMessageDto<SaasDtos.PlanRequestDto> approveRequest(@PathVariable Long id,
                                                                @Valid @RequestBody(required = false) OrderDecisionForm form) {
        return ApiMessageDto.ok(planRequestService.approve(id, form == null ? null : form.notes()));
    }

    @Operation(summary = "Reject a plan request")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_017)"),
            @ApiResponse(responseCode = "404", description = "No such request (SAAS_016)")})
    @PostMapping("/plan-requests/{id}/reject")
    public ApiMessageDto<SaasDtos.PlanRequestDto> rejectRequest(@PathVariable Long id,
                                                               @Valid @RequestBody(required = false) OrderDecisionForm form) {
        return ApiMessageDto.ok(planRequestService.reject(id, form == null ? null : form.notes()));
    }

    @Operation(summary = "Active / Inactive toggle of a plan", description = "Inactive plans are not offered; companies "
            + "already on one keep it until it ends.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Default plan made inactive (SAAS_015)"),
            @ApiResponse(responseCode = "404", description = "No such plan (SAAS_001)")})
    @PutMapping("/plans/{id}/status")
    public ApiMessageDto<PlanDto> planStatus(@PathVariable Long id, @Valid @RequestBody SaasDtos.PlanStatusForm form) {
        return ApiMessageDto.ok(catalog.setEnabled(id, form.enabled()));
    }

    @Operation(summary = "Approve a paid order", description = "Activates the plan for 1 month / 1 year, or extends "
            + "it when the company renews the plan it is on.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_009)"),
            @ApiResponse(responseCode = "404", description = "No such order (SAAS_008), plan deleted (SAAS_001)")})
    @PostMapping("/orders/{id}/approve")
    public ApiMessageDto<OrderDto> approve(@PathVariable Long id, @Valid @RequestBody(required = false) OrderDecisionForm form) {
        return ApiMessageDto.ok(orderService.approve(id, form == null ? null : form.notes()));
    }

    @Operation(summary = "Reject an order")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_009)"),
            @ApiResponse(responseCode = "404", description = "No such order (SAAS_008)")})
    @PostMapping("/orders/{id}/reject")
    public ApiMessageDto<OrderDto> reject(@PathVariable Long id, @Valid @RequestBody(required = false) OrderDecisionForm form) {
        return ApiMessageDto.ok(orderService.reject(id, form == null ? null : form.notes()));
    }

    @Operation(summary = "Set a company's plan directly (upgrade, gift, correction)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001) or plan (SAAS_001)"))
    @PutMapping("/companies/{companyId}/subscription")
    public ApiMessageDto<SubscriptionDto> assign(@PathVariable Long companyId,
                                                 @Valid @RequestBody SubscriptionAssignForm form) {
        return ApiMessageDto.ok(orderService.assign(companyId, form.planId(), form.expiresAt()));
    }
}
