package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SaasDtos {
    private SaasDtos() {
    }

    static String str(Long id) {
        return id == null ? null : id.toString();
    }

    // ------------------------------------------------------------------ plans

    public record PlanDto(String id, String code, String name, String description, BigDecimal monthlyPrice,
                          BigDecimal yearlyPrice, Integer maxUsers, Integer maxEmployees, BigDecimal storageLimitGb,
                          int trialDays, boolean aiIntegration, boolean defaultPlan, boolean enabled,
                          boolean recommended,
                          @Schema(description = "Free plan: prices are 0") boolean free,
                          @Schema(description = "Badge text on the plan card; null = none (recommended still shows the "
                                  + "Recommended badge)", example = "Most Popular") String badge,
                          @Schema(description = "Modules included; empty = every module (no restriction)")
                          java.util.List<EPlanModule> modules) {
        public static PlanDto of(Plan p) {
            return p == null ? null : new PlanDto(SaasDtos.str(p.getId()), p.getCode(), p.getName(), p.getDescription(),
                    p.getMonthlyPrice(), p.getYearlyPrice(), p.getMaxUsers(), p.getMaxEmployees(),
                    p.getStorageLimitGb(), p.getTrialDays(), p.isAiIntegration(), p.isDefaultPlan(), p.isEnabled(),
                    p.isRecommended(), p.isFree(), p.getBadge(), p.getModules().stream().sorted().toList());
        }
    }

    /** One entry of the module catalogue (plan form checkboxes, comparison table). */
    public record PlanModuleDto(EPlanModule code, String name,
                                @Schema(description = "false = no service yet: including or leaving it out changes nothing today")
                                boolean available) {
        public static PlanModuleDto of(EPlanModule m) {
            return new PlanModuleDto(m, m.getDisplayName(), m.available());
        }
    }

    public record PlanForm(
            @Schema(description = "Unique code, UPPER_SNAKE_CASE", example = "STARTER", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
            @Schema(description = "Display name", example = "Starter", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Optional; cleared when omitted on update", example = "Up to 20 accounts")
            @Size(max = 255) String description,
            @Schema(description = "Price per month", example = "490000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") BigDecimal monthlyPrice,
            @Schema(description = "Price per year; omit = no yearly billing", example = "4900000")
            @DecimalMin("0") BigDecimal yearlyPrice,
            @Schema(description = "Max accounts; omit = unlimited", example = "20") @Min(1) Integer maxUsers,
            @Schema(description = "Max employees (stored, enforced later); omit = unlimited", example = "50")
            @Min(1) Integer maxEmployees,
            @Schema(description = "Storage in GB (2 decimals, smallest 0.01), enforced by media-service on uploads; "
                    + "omit or -1 = unlimited", example = "5")
            @DecimalMin("-1") BigDecimal storageLimitGb,
            @Schema(description = "Trial days when given as the default plan to a new company", example = "14")
            @Min(0) int trialDays,
            @Schema(description = "Feature add-on: AI integration enabled for companies on this plan (flag only for now)",
                    example = "false") boolean aiIntegration,
            @Schema(description = "The plan new companies get and expired ones fall back to; setting it unsets the previous default",
                    example = "false") boolean defaultPlan,
            @Schema(description = "Offered to companies", example = "true") boolean enabled,
            @Schema(description = "Shows the Recommended badge on the plan card", example = "false")
            boolean recommended,
            @Schema(description = "Free plan: monthlyPrice must be 0 and yearlyPrice 0 or omitted (SAAS_041)",
                    example = "false") boolean free,
            @Schema(description = "Badge text on the plan card; omit = none. Shown instead of the Recommended badge "
                    + "when both are set", example = "Most Popular") @Size(max = 50) String badge,
            @Schema(description = "Modules included (full replacement); null or empty = every module. A company on the "
                    + "plan loses the permissions of the modules left out.", example = "[\"HRM\", \"RECRUITMENT\"]")
            java.util.Set<@NotNull EPlanModule> modules) {
    }

    public record PlanStatusForm(
            @Schema(description = "Active = offered to companies", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean enabled) {
    }

    // ------------------------------------------------------------------ subscription & orders

    public record SubscriptionDto(ESubscriptionStatus status, PlanDto plan, Instant expiresAt,
                                  @Schema(description = "true when the paid/trial plan expired and the default plan applies")
                                  boolean fallback, long usedUsers) {
    }

    public record SubscriptionAssignForm(
            @Schema(description = "Plan id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long planId,
            @Schema(description = "End of the period (UTC); omit = no expiry", example = "2027-01-31T00:00:00Z") Instant expiresAt) {
    }

    public record OrderForm(
            @Schema(description = "Plan id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long planId,
            @Schema(description = "MONTHLY or YEARLY", example = "MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EBillingCycle billingCycle,
            @Schema(description = "Optional coupon code", example = "WELCOME10") @Size(max = 50) String couponCode,
            @Schema(description = "Bank transfer reference, if already paid", example = "FT2611180001")
            @Size(max = 100) String paymentReference,
            @Schema(description = "BANK_TRANSFER (default), STRIPE or PAYPAL; STRIPE orders are paid "
                    + "with POST /saas/orders/{id}/stripe-checkout, PAYPAL with POST /saas/orders/{id}/paypal then "
                    + "/paypal/capture") EPaymentMethod paymentMethod) {
    }

    @Schema(description = "Redirect the browser to url; Stripe returns to the app when done")
    public record StripeCheckoutDto(String sessionId, String url) {
    }

    @Schema(description = "Where to send the payer: approveUrl; then call /orders/{orderId}/paypal/capture on return")
    public record PaypalCheckoutDto(String orderId, String paypalOrderId, String approveUrl) {
    }

    public record QuoteDto(BigDecimal originalPrice, BigDecimal discountAmount, BigDecimal finalPrice, String couponCode) {
    }

    public record BankTransferDto(String bankName, String accountNumber, String accountName, String transferContent) {
    }

    public record OrderDto(String id, String orderNumber, String companyId, String planId, EBillingCycle billingCycle,
                           String couponCode, BigDecimal originalPrice, BigDecimal discountAmount, BigDecimal finalPrice,
                           EPaymentMethod paymentMethod, String paymentReference, EOrderStatus status,
                           Instant createdDate, Instant processedAt, String notes, BankTransferDto bankTransfer,
                           @Schema(description = "Media file id of the payment receipt the company attached")
                           String receiptFileId) {
        public static OrderDto of(PlanOrder o, BankTransferDto bank) {
            return new OrderDto(SaasDtos.str(o.getId()), o.getOrderNumber(), SaasDtos.str(o.getCompanyId()), SaasDtos.str(o.getPlanId()),
                    o.getBillingCycle(), o.getCouponCode(), o.getOriginalPrice(), o.getDiscountAmount(),
                    o.getFinalPrice(), o.getPaymentMethod(), o.getPaymentReference(), o.getStatus(),
                    o.getCreatedDate(), o.getProcessedAt(), o.getNotes(), bank, SaasDtos.str(o.getReceiptFileId()));
        }
    }

    @Schema(description = "Admin orders list row: the order plus the names the table shows")
    public record AdminOrderDto(OrderDto order, String companyName, String companyEmail, String planName) {
    }

    @Schema(description = "Counts for the status tabs, with the same filters as the list")
    public record StatusCountsDto(long all, long pending, long approved, long rejected, long cancelled) {
        public static StatusCountsDto of(java.util.List<Object[]> rows) {
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            rows.forEach(r -> m.put(String.valueOf(r[0]), (Long) r[1]));
            long p = m.getOrDefault("PENDING", 0L), a = m.getOrDefault("APPROVED", 0L),
                    rj = m.getOrDefault("REJECTED", 0L), c = m.getOrDefault("CANCELLED", 0L);
            return new StatusCountsDto(p + a + rj + c, p, a, rj, c);
        }
    }

    public record PlanRequestForm(
            @Schema(description = "Requested plan id", example = "4", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long planId,
            @Schema(description = "MONTHLY or YEARLY", example = "MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EBillingCycle billingCycle,
            @Schema(description = "Optional note to the super admin", example = "We grow to 30 accounts next month")
            @Size(max = 500) String message) {
    }

    public record PlanRequestDto(String id, String companyId, String companyName, String companyEmail, String planId,
                                 String planName, EBillingCycle billingCycle, String message, EPlanRequestStatus status,
                                 Instant createdDate, Instant processedAt, String notes, String orderId) {
        public static PlanRequestDto of(com.terp.erp.iam.model.PlanRequest r, String companyName, String companyEmail,
                                        String planName) {
            return new PlanRequestDto(str(r.getId()), str(r.getCompanyId()), companyName, companyEmail,
                    str(r.getPlanId()), planName, r.getBillingCycle(), r.getMessage(), r.getStatus(),
                    r.getCreatedDate(), r.getProcessedAt(), r.getNotes(), str(r.getOrderId()));
        }
    }

    public record OrderDecisionForm(
            @Schema(description = "Note shown to the company (reason when rejecting)", example = "Transfer not received")
            @Size(max = 500) String notes) {
    }
}
