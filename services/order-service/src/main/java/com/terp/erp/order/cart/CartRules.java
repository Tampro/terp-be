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
                   boolean minimumReached) {
    }

    public static Summary summary(StoreView store, List<Priced> lines, boolean bagRequested) {
        BigDecimal subtotal = lines.stream().filter(Priced::available).map(Priced::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal free = nz(store.freeDeliveryThreshold());
        BigDecimal delivery = free.signum() > 0 && subtotal.compareTo(free) >= 0 ? BigDecimal.ZERO : nz(store.deliveryFee());
        BigDecimal bag = bagRequested ? nz(store.bagFee()) : BigDecimal.ZERO;
        // TODO(assumption): no orders exist yet, so every customer is on their first order (FR-ORD-002: 10,000 then
        // 20,000), and "after discounts" is the subtotal until coupons exist.
        BigDecimal minimum = nz(store.firstOrderMinAmount()).signum() > 0 ? nz(store.firstOrderMinAmount())
                : nz(store.minOrderAmount());
        return new Summary(subtotal, delivery, bag, subtotal.add(delivery).add(bag), minimum,
                subtotal.compareTo(minimum) >= 0);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
