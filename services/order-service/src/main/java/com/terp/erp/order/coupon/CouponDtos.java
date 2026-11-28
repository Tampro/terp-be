package com.terp.erp.order.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CouponDtos {
    private CouponDtos() {
    }

    public record CouponForm(
            @Schema(description = "Code the customer types; stored upper-case, unique in the company", example = "WELCOME10",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,30}$") String code,
            @Schema(example = "Welcome 10%", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ECouponType type,
            @Schema(description = "PERCENT: 1-100; AMOUNT: won", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal value,
            @Schema(description = "Cap of a PERCENT discount (won)", example = "5000") @DecimalMin("0") BigDecimal maxDiscount,
            @Schema(description = "Goods subtotal needed (won)", example = "20000") @DecimalMin("0") BigDecimal minSubtotal,
            @Schema(description = "From (inclusive, UTC)", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Instant startsAt,
            @Schema(description = "Until (exclusive, UTC)", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Instant endsAt,
            @Schema(description = "Uses in total; null = no limit", example = "1000") @Min(1) Integer totalLimit,
            @Schema(description = "Uses per customer; null = no limit", example = "1") @Min(1) Integer perCustomerLimit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean active) {
    }

    public record CouponDto(String id, String code, String name, ECouponType type, BigDecimal value, BigDecimal maxDiscount,
                            BigDecimal minSubtotal, Instant startsAt, Instant endsAt, Integer totalLimit,
                            Integer perCustomerLimit, int usedCount, boolean active, Instant createdDate) {
        static CouponDto of(Coupon c) {
            return new CouponDto(String.valueOf(c.getId()), c.getCode(), c.getName(), c.getType(), c.getValue(),
                    c.getMaxDiscount(), c.getMinSubtotal(), c.getStartsAt(), c.getEndsAt(), c.getTotalLimit(),
                    c.getPerCustomerLimit(), c.getUsedCount(), c.isActive(), c.getCreatedDate());
        }
    }
}
