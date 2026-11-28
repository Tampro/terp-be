package com.terp.erp.order.cart;

import com.terp.erp.order.client.CartSourcesClient.StoreView;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Express order rules on a cart (FR-ORD-002), as a pure function so they are tested apart from any service:
 * delivery free from the store's threshold, the paid bag when asked, the minimum order — compared with the subtotal of
 * the available lines only.
 */
public final class CartRules {
    private CartRules() {
    }

    public record Priced(BigDecimal lineTotal, boolean available) {
    }

    public record Summary(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal bagFee, BigDecimal total, BigDecimal minOrderAmount,
                   boolean minimumReached, BigDecimal discount, BigDecimal pointsUsed) {
    }

    public static Summary summary(StoreView store, List<Priced> lines, boolean bagRequested) {
        return summary(store, lines, bagRequested, BigDecimal.ZERO);
    }

    /** The goods subtotal of the available lines (what a coupon discounts). */
    public static BigDecimal subtotal(List<Priced> lines) {
        return lines.stream().filter(Priced::available).map(Priced::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * discount: a coupon's, off the goods only. Free delivery and the minimum order compare the goods subtotal after
     * the discount (plan P2 "minimum after discounts").
     */
    public static Summary summary(StoreView store, List<Priced> lines, boolean bagRequested, BigDecimal discount) {
        return summary(store, lines, bagRequested, discount, BigDecimal.ZERO);
    }

    /** points: mileage spent after the coupon, off the goods only; the minimum and free delivery are on what is left. */
    public static Summary summary(StoreView store, List<Priced> lines, boolean bagRequested, BigDecimal discount,
                                  BigDecimal points) {
        BigDecimal subtotal = subtotal(lines);
        BigDecimal off = discount == null ? BigDecimal.ZERO : discount.min(subtotal);
        BigDecimal used = points == null ? BigDecimal.ZERO : points.min(subtotal.subtract(off));
        BigDecimal net = subtotal.subtract(off).subtract(used);
        BigDecimal free = nz(store.freeDeliveryThreshold());
        BigDecimal delivery = free.signum() > 0 && net.compareTo(free) >= 0 ? BigDecimal.ZERO : nz(store.deliveryFee());
        BigDecimal bag = bagRequested ? nz(store.bagFee()) : BigDecimal.ZERO;
        // TODO(assumption): every customer is treated as on their first order (FR-ORD-002: 10,000 then 20,000).
        BigDecimal minimum = nz(store.firstOrderMinAmount()).signum() > 0 ? nz(store.firstOrderMinAmount())
                : nz(store.minOrderAmount());
        return new Summary(subtotal, delivery, bag, net.add(delivery).add(bag), minimum, net.compareTo(minimum) >= 0, off,
                used);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
