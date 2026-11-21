package com.terp.erp.iam.referral;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Referral program (docs/analysis/06 §3.2.6). Money is in the default currency. */
public final class ReferralDtos {
    private ReferralDtos() {
    }

    static String str(Long id) {
        return id == null ? null : id.toString();
    }

    // ------------------------------------------------------------------ settings

    public record SettingsDto(boolean enabled, BigDecimal commissionPercentage, BigDecimal thresholdAmount,
                              String guidelines, Instant updatedDate) {
        static SettingsDto of(ReferralSetting s) {
            return new SettingsDto(s.isEnabled(), s.getCommissionPercentage(), s.getThresholdAmount(),
                    s.getGuidelines(), s.getUpdatedDate());
        }
    }

    public record SettingsForm(
            @Schema(description = "Enable Referral Program", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean enabled,
            @Schema(description = "Commission Percentage (%)", example = "10.00", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercentage,
            @Schema(description = "Minimum Threshold Amount (default currency)", example = "500000",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") BigDecimal thresholdAmount,
            @Schema(description = "Referral Guidelines shown to referrers; cleared when omitted", example = "Earn 10% ...")
            @Size(max = 10000) String guidelines) {
    }

    // ------------------------------------------------------------------ company side

    @Schema(description = "My referral page: code to share, rules, and balance")
    public record MyReferralDto(String referralCode, boolean programEnabled, BigDecimal commissionPercentage,
                                BigDecimal thresholdAmount, String guidelines, long referredCompanies,
                                BigDecimal totalEarned, BigDecimal paidOut, BigDecimal pendingPayout,
                                BigDecimal availableBalance) {
    }

    public record PayoutForm(
            @Schema(description = "Amount to be paid (≥ threshold, ≤ available balance)", example = "500000",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount) {
    }

    public record PayoutDto(String id, String companyId, String companyName, String companyEmail, BigDecimal amount,
                            EPayoutStatus status, String notes, Instant createdDate, Instant processedAt) {
        static PayoutDto of(PayoutRequest p, String name, String email) {
            return new PayoutDto(str(p.getId()), str(p.getCompanyId()), name, email, p.getAmount(), p.getStatus(),
                    p.getNotes(), p.getCreatedDate(), p.getProcessedAt());
        }
    }

    public record PayoutDecisionForm(
            @Schema(description = "Note (payment reference, or reason when rejecting)", example = "Paid FT2611190001")
            @Size(max = 500) String notes) {
    }

    @Schema(description = "Counts for the payout status tabs")
    public record PayoutCountsDto(long all, long pending, long approved, long rejected) {
    }

    // ------------------------------------------------------------------ super admin

    public record TopReferrerDto(String companyId, String companyName, String companyEmail, long referrals,
                                 BigDecimal commissionEarned) {
    }

    @Schema(description = "Dashboard: four cards, top referring companies, this year's figures")
    public record DashboardDto(long totalReferredCompanies, BigDecimal pendingPayouts, BigDecimal totalCommissionPaid,
                               long activeReferredCompanies, List<TopReferrerDto> topReferrers,
                               long referralSignupsThisYear, BigDecimal payoutsProcessedThisYear) {
    }

    public record CommissionDto(String orderId, BigDecimal orderAmount, BigDecimal commissionPercentage,
                                BigDecimal amount, Instant createdDate) {
        static CommissionDto of(ReferralCommission c) {
            return new CommissionDto(str(c.getOrderId()), c.getOrderAmount(), c.getCommissionPercentage(),
                    c.getAmount(), c.getCreatedDate());
        }
    }

    public record ReferredCompanyDto(String companyId, String companyName, String companyEmail, Instant registeredAt,
                                     String referrerCompanyId, String referrerName, String planName,
                                     BigDecimal planMonthlyPrice, BigDecimal commission,
                                     List<CommissionDto> commissionHistory) {
    }

    @Schema(description = "Referred Users summary cards")
    public record ReferredSummaryDto(long totalReferred, long withPlans, BigDecimal totalCommission) {
    }
}
