package com.terp.erp.order.mileage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MileageDtos {
    private MileageDtos() {
    }

    public record PolicyForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean active,
            @Schema(description = "% of the goods (after coupon and points) earned on delivery", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 2) BigDecimal earnRate,
            @Schema(description = "Months a lot of points lasts", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Min(1) @Max(60) Integer expiryMonths,
            @Schema(description = "Least points used on an order (multiple of 10)", example = "1000",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(1000000) Integer minRedeem) {
    }

    public record PolicyDto(boolean active, BigDecimal earnRate, int expiryMonths, int minRedeem, boolean saved) {
        static PolicyDto of(MileagePolicy p, boolean saved) {
            return new PolicyDto(p.isActive(), p.getEarnRate(), p.getExpiryMonths(), p.getMinRedeem(), saved);
        }
    }

    public record AdjustForm(
            @Schema(description = "Points to add (+) or take (−); never 0", example = "500",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(-1000000) @Max(1000000) Integer points,
            @Schema(example = "Goodwill for a late delivery", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String reason) {
    }

    public record EntryDto(String id, EMileageEntryType type, int points, String orderNo, String reason,
                           Instant createdDate) {
        static EntryDto of(MileageEntry e) {
            return new EntryDto(String.valueOf(e.getId()), e.getType(), e.getPoints(), e.getOrderNo(), e.getReason(),
                    e.getCreatedDate());
        }
    }

    public record AccountDto(String customerId, long balance,
                             @Schema(description = "Points that expire within 30 days") long expiringSoon,
                             List<EntryDto> entries, long totalEntries) {
    }
}
