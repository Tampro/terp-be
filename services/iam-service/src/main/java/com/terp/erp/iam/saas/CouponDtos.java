package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.iam.model.Coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Coupons page (docs/analysis/06 §3.2.4). */
public final class CouponDtos {
    private CouponDtos() {
    }

    /**
     * @param timesUsed orders that consume a use: PENDING + APPROVED (a rejected or cancelled order gives it back)
     */
    public record CouponDto(String id, String code, ECouponCodeType codeGeneration, String name, ECouponType type,
                            BigDecimal discountValue, BigDecimal minimumSpend, BigDecimal maximumSpend,
                            Integer useLimitTotal, Integer useLimitPerCompany, LocalDate expiryDate, boolean enabled,
                            long timesUsed, Instant createdDate) {
        static CouponDto of(Coupon c, long timesUsed) {
            return new CouponDto(SaasDtos.str(c.getId()), c.getCode(), c.getCodeType(), c.getName(), c.getType(),
                    c.getDiscountValue(), c.getMinimumSpend(), c.getMaximumSpend(), c.getUseLimitTotal(),
                    c.getUseLimitPerCompany(), c.getExpiryDate(), c.isEnabled(), timesUsed, c.getCreatedDate());
        }
    }

    public record CouponForm(
            @Schema(description = "Display name", example = "Summer Sale 2026", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "PERCENTAGE or FLAT", example = "PERCENTAGE", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ECouponType type,
            @Schema(description = "Percent (0–100) or amount", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") BigDecimal discountValue,
            @Schema(description = "MANUAL (default): `code` is required. AUTO: the server generates an 8-character code "
                    + "on create; on update the existing code is kept.", example = "MANUAL")
            ECouponCodeType codeGeneration,
            @Schema(description = "Unique code (MANUAL)", example = "SUMMER25")
            @Size(max = 50) @Pattern(regexp = "[A-Z0-9_-]+") String code,
            @Schema(description = "Minimum order price; omit = none", example = "400000") @DecimalMin("0") BigDecimal minimumSpend,
            @Schema(description = "Maximum order price eligible; omit = none", example = "10000000")
            @DecimalMin("0") BigDecimal maximumSpend,
            @Schema(description = "Total uses; omit = unlimited", example = "100") @Min(1) Integer useLimitTotal,
            @Schema(description = "Uses per company; omit = unlimited", example = "1") @Min(1) Integer useLimitPerCompany,
            @Schema(description = "Last valid day (UTC); omit = no expiry", example = "2026-12-31") LocalDate expiryDate,
            @Schema(description = "Active; omitted = true", example = "true") Boolean enabled) {
    }

    public record CouponStatusForm(
            @Schema(description = "Active", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean enabled) {
    }

    @Schema(description = "Counts for the status tabs (same filters as the list)")
    public record CouponCountsDto(long all, long active, long inactive) {
    }

    @Schema(description = "One order that used the coupon")
    public record CouponUsageDto(String orderId, String orderNumber, String companyId, String companyName,
                                 String companyEmail, BigDecimal orderAmount, BigDecimal discountApplied,
                                 BigDecimal finalPrice, EOrderStatus status, Instant usedAt) {
    }

    @Schema(description = "View Details: the coupon (summary cards + information) and its usage history, newest first")
    public record CouponDetailDto(CouponDto coupon, List<CouponUsageDto> usageHistory) {
    }
}
