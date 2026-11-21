package com.terp.erp.asset.depreciation;

import org.junit.jupiter.api.Test;
import com.terp.erp.asset.item.Asset;
import com.terp.erp.asset.item.Asset.EDepreciationMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DepreciationMathTest {
    @Test
    void straightLine() {
        // 24,000,000 − 0 over 4 years = 500,000 a month; 18 whole months by 2027-07-20
        var r = DepreciationMath.of(asset(EDepreciationMethod.STRAIGHT_LINE, "24000000", "0", 4),
                LocalDate.of(2027, 7, 20));
        assertThat(r.monthsElapsed()).isEqualTo(18);
        assertThat(r.monthlyDepreciation()).isEqualByComparingTo("500000");
        assertThat(r.annualDepreciation()).isEqualByComparingTo("6000000");
        assertThat(r.accumulatedDepreciation()).isEqualByComparingTo("9000000");
        assertThat(r.currentValue()).isEqualByComparingTo("15000000");
        assertThat(r.depreciatedPercent()).isEqualByComparingTo("37.5");
        assertThat(r.lifeConsumedPercent()).isEqualByComparingTo("37.5");
    }

    @Test
    void straightLineStopsAtSalvage() {
        var r = DepreciationMath.of(asset(EDepreciationMethod.STRAIGHT_LINE, "12000000", "2000000", 2),
                LocalDate.of(2035, 1, 1));
        assertThat(r.monthsElapsed()).isEqualTo(24);
        assertThat(r.currentValue()).isEqualByComparingTo("2000000");
        assertThat(r.lifeConsumedPercent()).isEqualByComparingTo("100");
    }

    @Test
    void decliningBalance() {
        // 4 years: 50 % a year. Year 1: 16,000,000 → 8,000,000. 6 months into year 2: 8,000,000 − 4,000,000 × 6/12
        var r = DepreciationMath.of(asset(EDepreciationMethod.DECLINING_BALANCE, "16000000", "1000000", 4),
                LocalDate.of(2027, 7, 15));
        assertThat(r.monthsElapsed()).isEqualTo(18);
        assertThat(r.annualDepreciation()).isEqualByComparingTo("4000000");
        assertThat(r.currentValue()).isEqualByComparingTo("6000000");
        // after the life, exactly the salvage value
        var end = DepreciationMath.of(asset(EDepreciationMethod.DECLINING_BALANCE, "16000000", "1000000", 4),
                LocalDate.of(2031, 1, 15));
        assertThat(end.currentValue()).isEqualByComparingTo("1000000");
        assertThat(end.annualDepreciation()).isEqualByComparingTo("0");
    }

    @Test
    void nothingWithoutLifeOrDate() {
        Asset a = asset(EDepreciationMethod.STRAIGHT_LINE, "5000000", "0", 0);
        assertThat(DepreciationMath.of(a, LocalDate.of(2030, 1, 1)).currentValue()).isEqualByComparingTo("5000000");
        a.setUsefulLifeYears(3);
        a.setPurchaseDate(null);
        var r = DepreciationMath.of(a, LocalDate.of(2030, 1, 1));
        assertThat(r.currentValue()).isEqualByComparingTo("5000000");
        assertThat(r.monthsElapsed()).isZero();
        // bought in the future: nothing yet
        a.setPurchaseDate(LocalDate.of(2031, 1, 1));
        assertThat(DepreciationMath.of(a, LocalDate.of(2030, 1, 1)).accumulatedDepreciation())
                .isEqualByComparingTo("0");
    }

    private static Asset asset(EDepreciationMethod m, String cost, String salvage, int years) {
        Asset a = new Asset();
        a.setDepreciationMethod(m);
        a.setPurchaseCost(new BigDecimal(cost));
        a.setSalvageValue(new BigDecimal(salvage));
        a.setUsefulLifeYears(years);
        a.setPurchaseDate(LocalDate.of(2026, 1, 15));
        return a;
    }
}
