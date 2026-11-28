package com.terp.erp.order.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Amounts of the SHOP_ORDER journal of a paid order, as accounting's posting expects them (net of VAT by role).
 * TODO(assumption): prices and fees include 10% VAT, VAT = amount / 11 rounded to whole won; cost = purchase price at
 *  the stock move — chief accountant to confirm (order-invoice spec).
 */
public final class OrderJournal {
    public static final String SOURCE_TYPE = "SHOP_ORDER";
    public static final String EVENT_REQUESTED = "commerce.journal.requested";
    private static final BigDecimal ELEVEN = BigDecimal.valueOf(11);

    private OrderJournal() {
    }

    /** goods / service net of VAT, tax = VAT of both, cost = cost of goods; goods + service + tax = order total. */
    public record Amounts(BigDecimal goods, BigDecimal service, BigDecimal tax, BigDecimal cost) {
    }

    public static Amounts amounts(CustomerOrder o, List<CustomerOrderLine> lines) {
        BigDecimal g = lines.stream().map(CustomerOrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal f = nz(o.getDeliveryFee()).add(nz(o.getBagFee()));
        BigDecimal vatG = vat(g);
        BigDecimal vatF = vat(f);
        BigDecimal c = lines.stream().map(l -> nz(l.getUnitCost()).multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Amounts(g.subtract(vatG), f.subtract(vatF), vatG.add(vatF), c);
    }

    static BigDecimal vat(BigDecimal grossIncludingVat) {
        return grossIncludingVat.divide(ELEVEN, 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
