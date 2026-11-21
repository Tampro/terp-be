package com.terp.erp.commerce.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Line arithmetic shared by documents and returns: base = qty × price; discount = base × pct / 100;
 * tax = (base − discount) × rate / 100; total = base − discount + tax. Each amount rounded to 2 decimals, half up.
 */
// TODO(assumption): rounding per line to 2 decimals half-up (the source gives no rounding rule) — confirm with the accountant.
public final class Money {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public record Line(BigDecimal base, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    public static Line line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal taxRate) {
        BigDecimal base = round(quantity.multiply(unitPrice));
        BigDecimal discount = round(base.multiply(nz(discountPct)).divide(HUNDRED));
        BigDecimal tax = round(base.subtract(discount).multiply(nz(taxRate)).divide(HUNDRED));
        return new Line(base, discount, tax, base.subtract(discount).add(tax));
    }

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
