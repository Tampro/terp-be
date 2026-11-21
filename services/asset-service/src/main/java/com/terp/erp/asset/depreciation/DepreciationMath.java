package com.terp.erp.asset.depreciation;

import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.Asset.EDepreciationMethod;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Depreciation of one asset at a date (docs/analysis/06 §8.2.2 Depreciation tab, §8.2.3; PO 2026-11-20):
 * <ul>
 *   <li>time = whole months since the purchase date, at most the useful life;</li>
 *   <li>STRAIGHT_LINE: (cost − salvage) / life months per month;</li>
 *   <li>DECLINING_BALANCE (double): 2 / life years of the value at the start of each year, the running year spread
 *   evenly over its months; never below the salvage value, and exactly the salvage value once the life is over;</li>
 *   <li>no purchase date or no useful life: nothing depreciates.</li>
 * </ul>
 * Amounts are rounded half-up to 2 decimals.
 * TODO(assumption): monthly granularity (no partial month) and depreciation still counted for disposed assets —
 * confirm with BA.
 */
public final class DepreciationMath {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final MathContext MC = MathContext.DECIMAL64;

    private DepreciationMath() {
    }

    /**
     * annual / monthly = the depreciation of the running year (declining) or of every year (straight line);
     * depreciatedPercent of the cost, lifeConsumedPercent of the useful life.
     */
    public record Result(EDepreciationMethod method, BigDecimal purchaseCost, BigDecimal salvageValue,
                         BigDecimal depreciableAmount, int usefulLifeMonths, int monthsElapsed,
                         BigDecimal annualDepreciation, BigDecimal monthlyDepreciation,
                         BigDecimal accumulatedDepreciation, BigDecimal currentValue, BigDecimal depreciatedPercent,
                         BigDecimal lifeConsumedPercent) {
    }

    public static Result of(Asset a, LocalDate today) {
        BigDecimal cost = nz(a.getPurchaseCost());
        BigDecimal salvage = nz(a.getSalvageValue()).min(cost);
        BigDecimal depreciable = cost.subtract(salvage);
        int years = Math.max(0, a.getUsefulLifeYears());
        int life = years * 12;
        int elapsed = 0;
        if (a.getPurchaseDate() != null && life > 0 && today.isAfter(a.getPurchaseDate())) {
            elapsed = (int) Math.min(life, ChronoUnit.MONTHS.between(a.getPurchaseDate(), today));
        }
        BigDecimal annual = BigDecimal.ZERO;
        BigDecimal value = cost;
        if (life > 0 && depreciable.signum() > 0) {
            if (a.getDepreciationMethod() == EDepreciationMethod.DECLINING_BALANCE) {
                BigDecimal rate = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(years), MC);
                BigDecimal start = cost;
                for (int y = 0; y < elapsed / 12; y++) {
                    start = start.subtract(start.multiply(rate, MC)).max(salvage);
                }
                annual = start.multiply(rate, MC).min(start.subtract(salvage));
                value = start.subtract(annual.multiply(BigDecimal.valueOf(elapsed % 12), MC).divide(TWELVE, MC));
                if (elapsed >= life) {
                    value = salvage;
                }
            } else {
                annual = depreciable.multiply(TWELVE, MC).divide(BigDecimal.valueOf(life), MC);
                value = cost.subtract(depreciable.multiply(BigDecimal.valueOf(elapsed), MC)
                        .divide(BigDecimal.valueOf(life), MC));
            }
            value = value.max(salvage);
        }
        if (elapsed >= life && life > 0 && a.getDepreciationMethod() == EDepreciationMethod.DECLINING_BALANCE) {
            annual = BigDecimal.ZERO;
        }
        BigDecimal accumulated = cost.subtract(value);
        return new Result(a.getDepreciationMethod(), money(cost), money(salvage), money(depreciable), life, elapsed,
                money(annual), money(annual.divide(TWELVE, MC)), money(accumulated), money(value),
                percent(accumulated, cost), percent(BigDecimal.valueOf(elapsed), BigDecimal.valueOf(life)));
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO
                : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
