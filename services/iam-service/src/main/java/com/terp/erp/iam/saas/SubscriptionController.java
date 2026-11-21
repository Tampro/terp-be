package com.terp.erp.iam.saas;

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
import com.terp.erp.iam.saas.SaasDtos.OrderDto;
import com.terp.erp.iam.saas.SaasDtos.OrderForm;
import com.terp.erp.iam.saas.SaasDtos.PlanDto;
import com.terp.erp.iam.saas.SaasDtos.QuoteDto;
import com.terp.erp.iam.saas.SaasDtos.SubscriptionDto;

import java.util.List;

/**
 * Company side. These permissions are all a COMPANY_ADMIN keeps while the subscription is EXPIRED, so renewing
 * always works.
 */
@Tag(name = "SaaS — my subscription")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saas")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {
    private final CatalogService catalog;
    private final OrderService orderService;
    private final PlanRequestService planRequests;

    @Operation(summary = "Modules a plan can include",
            description = "For the plan form and the comparison table. available = false: no service yet, so "
                    + "including or leaving the module out changes nothing today.")
    @PreAuthorize("@PermissionChecker.has('SAAS_SUBSCRIPTION_VIEW')")
    @GetMapping("/plan-modules")
    public ApiMessageDto<List<SaasDtos.PlanModuleDto>> planModules() {
        return ApiMessageDto.ok(catalog.moduleCatalogue());
    }

    @Operation(summary = "Plans offered to companies")
    @PreAuthorize("@PermissionChecker.has('SAAS_SUBSCRIPTION_VIEW')")
    @GetMapping("/plans")
    public ApiMessageDto<List<PlanDto>> plans() {
        return ApiMessageDto.ok(catalog.offeredPlans());
    }

    @Operation(summary = "Current subscription of my company",
            description = "status: NONE (no plan, no limits), TRIAL, ACTIVE, EXPIRED. fallback = the paid or trial "
                    + "plan expired and the default plan applies.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('SAAS_SUBSCRIPTION_VIEW')")
    @GetMapping("/subscription")
    public ApiMessageDto<SubscriptionDto> subscription(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(orderService.mySubscription(companyId));
    }

    @Operation(summary = "Start Trial of a plan", description = "The company uses the plan for its trial days, once "
            + "per plan; not while a paid period runs. When the trial ends the default plan applies.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Plan not offered (SAAS_014), no trial (SAAS_034), "
                    + "already tried (SAAS_035), a paid plan is running (SAAS_036)"),
            @ApiResponse(responseCode = "404", description = "No such plan (SAAS_001)")})
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/plans/{id}/trial")
    public ApiMessageDto<SubscriptionDto> startTrial(@PathVariable Long id) {
        return ApiMessageDto.ok(orderService.startTrial(id));
    }

    @Operation(summary = "Price an order (with coupon) without creating it")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "No yearly price (SAAS_003), coupon invalid "
            + "(SAAS_006), below minimum spend (SAAS_007), plan not offered (SAAS_014)"))
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/orders/quote")
    public ApiMessageDto<QuoteDto> quote(@Valid @RequestBody OrderForm form) {
        return ApiMessageDto.ok(orderService.quote(form));
    }

    @Operation(summary = "Order a plan (bank transfer)",
            description = "Creates a PENDING order; transfer `finalPrice` with `bankTransfer.transferContent` as the "
                    + "content. A super admin approves it to activate the plan.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Pending order exists (SAAS_010), no yearly "
            + "price (SAAS_003), coupon invalid (SAAS_006), below minimum spend (SAAS_007), plan not offered (SAAS_014)"))
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/orders")
    public ApiMessageDto<OrderDto> order(@Valid @RequestBody OrderForm form) {
        return ApiMessageDto.ok(orderService.create(form));
    }

    @Operation(summary = "Orders of my company")
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_VIEW')")
    @GetMapping("/orders")
    public ApiMessageDto<ResponseListDto<OrderDto>> orders(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(orderService.myOrders(companyId, pageable)));
    }

    @Operation(summary = "Request a plan (no online payment)", description = "A super admin approves or rejects it. "
            + "One pending request per company.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Pending request exists (SAAS_018), plan not "
            + "offered (SAAS_014), no yearly price (SAAS_003)"))
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/plan-requests")
    public ApiMessageDto<SaasDtos.PlanRequestDto> requestPlan(@Valid @RequestBody SaasDtos.PlanRequestForm form) {
        return ApiMessageDto.ok(planRequests.create(form));
    }

    @Operation(summary = "Plan requests of my company")
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_VIEW')")
    @GetMapping("/plan-requests")
    public ApiMessageDto<ResponseListDto<SaasDtos.PlanRequestDto>> myRequests(
            @Parameter(description = "Super admin only") @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(planRequests.mine(companyId, pageable)));
    }

    @Operation(summary = "Cancel a pending plan request")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_017)"),
            @ApiResponse(responseCode = "404", description = "No such request in your company (SAAS_016)")})
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_CREATE')")
    @PostMapping("/plan-requests/{id}/cancel")
    public ApiMessageDto<SaasDtos.PlanRequestDto> cancelRequest(@PathVariable Long id) {
        return ApiMessageDto.ok(planRequests.cancel(id));
    }

    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_CREATE')")
    @PutMapping("/orders/{id}/receipt")
    @Operation(summary = "Attach the payment receipt to a pending order",
            description = "fileId: a PDF, JPG or PNG of your company's media library (max 5 MB); null removes it. "
                    + "The super admin sees it on Plan Orders (receiptFileId).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_009), not a PDF/JPG/PNG of your "
                    + "company up to 5 MB or no access to it (SAAS_033)"),
            @ApiResponse(responseCode = "404", description = "No such order in your company (SAAS_008)"),
            @ApiResponse(responseCode = "503", description = "media-service unreachable (IAM_025)")})
    public ApiMessageDto<OrderDto> receipt(@PathVariable Long id, @RequestBody ReceiptForm form) {
        return ApiMessageDto.ok(orderService.attachReceipt(id, form.fileId()));
    }

    public record ReceiptForm(@io.swagger.v3.oas.annotations.media.Schema(description = "Media file id; null removes")
                              Long fileId) {
    }

    @Operation(summary = "Pay a pending order with Stripe",
            description = "For an order created with paymentMethod STRIPE. Returns the Stripe Checkout URL to send "
                    + "the browser to; the order is approved by the Stripe webhook once paid.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a pending STRIPE order with an amount (SAAS_039), "
                    + "Stripe off or no default currency (SAAS_037), Stripe refused (SAAS_038)"),
            @ApiResponse(responseCode = "404", description = "No such order in your company (SAAS_008)")})
    @PreAuthorize("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')")
    @PostMapping("/orders/{id}/stripe-checkout")
    public ApiMessageDto<SaasDtos.StripeCheckoutDto> stripeCheckout(@PathVariable Long id) {
        return ApiMessageDto.ok(orderService.stripeCheckout(id));
    }

    @Operation(summary = "Cancel a pending order")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (SAAS_009)"),
            @ApiResponse(responseCode = "404", description = "No such order in your company (SAAS_008)")})
    @PreAuthorize("@PermissionChecker.has('SAAS_ORDER_CREATE')")
    @PostMapping("/orders/{id}/cancel")
    public ApiMessageDto<OrderDto> cancel(@PathVariable Long id) {
        return ApiMessageDto.ok(orderService.cancel(id));
    }
}
