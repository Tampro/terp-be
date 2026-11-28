package com.terp.erp.order.cart;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.cart.CartRules.Priced;
import com.terp.erp.order.cart.CartRules.Summary;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.client.CartSourcesClient.ShelfItem;
import com.terp.erp.order.client.CartSourcesClient.StoreView;
import com.terp.erp.order.common.EOrderError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One cart per (membership, store), priced live. The membership comes from member-service (token subject + shop), the
 * store from store-service (must belong to the shop), prices / limits / sold-out from inventory — nothing is stored but
 * item ids and quantities.
 */
@Service
@RequiredArgsConstructor
public class CartService {
    static final int MAX_LINES = 100;

    private final CartRepository carts;
    private final CartLineRepository lines;
    private final CartSourcesClient sources;
    private final com.terp.erp.order.coupon.CouponLedger coupons;

    public record QuantityForm(@Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    public record KeepForm(@NotNull Boolean keep) {
    }

    public record BagForm(@NotNull Boolean bagRequested) {
    }

    public record LineDto(String itemId, String name, BigDecimal salePrice, int quantity, BigDecimal lineTotal,
                          Integer purchaseLimitQty, boolean soldOut, boolean available, boolean keep, Instant addedAt) {
    }

    public record CartDto(String storeId, List<LineDto> lines, BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal bagFee,
                          boolean bagRequested, BigDecimal total, BigDecimal minOrderAmount, boolean minimumReached,
                          BigDecimal freeDeliveryThreshold, boolean orderableNow, Instant opensAt,
                          @Schema(description = "The coupon code previewed (upper-case), null without one") String couponCode,
                          @Schema(description = "Coupon discount off the goods (0 without / with a refused coupon)") BigDecimal discount,
                          @Schema(description = "Why the coupon does not apply (CPN_007-CPN_011), null when it does")
                          String couponError) {
    }

    private record Context(Membership member, StoreView store, Cart cart) {
    }

    @Transactional
    public CartDto view(String slug, String sub, long storeId) {
        return dto(context(slug, sub, storeId));
    }

    /** The cart with a coupon previewed: the refusal comes back as couponError, the read never fails for it. */
    @Transactional
    public CartDto view(String slug, String sub, long storeId, String couponCode) {
        Context ctx = context(slug, sub, storeId);
        return couponCode == null || couponCode.isBlank() ? dto(ctx) : dto(ctx, couponCode);
    }

    /** Creates the line or replaces its quantity. */
    @Transactional
    public CartDto setQuantity(String slug, String sub, long storeId, long itemId, int quantity) {
        Context ctx = context(slug, sub, storeId);
        ShelfItem item = sources.shelf(ctx.member().companyId(), ctx.store().warehouseId(), List.of(itemId)).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound(EOrderError.ORD_003));
        if (item.purchaseLimitQty() != null && quantity > item.purchaseLimitQty()) {
            throw ApiException.badRequest(EOrderError.ORD_002, item.purchaseLimitQty());
        }
        CartLine line = lines.findByCartIdAndItemId(ctx.cart().getId(), itemId).orElseGet(() -> {
            if (lines.countByCartId(ctx.cart().getId()) >= MAX_LINES) {
                throw ApiException.badRequest(EOrderError.ORD_004, MAX_LINES);
            }
            CartLine n = new CartLine();
            n.setCartId(ctx.cart().getId());
            n.setItemId(itemId);
            return n;
        });
        line.setQuantity(quantity);
        try {
            lines.saveAndFlush(line);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, EOrderError.ORD_007); // concurrent add, retry
        }
        return dto(ctx);
    }

    @Transactional
    public CartDto remove(String slug, String sub, long storeId, long itemId) {
        Context ctx = context(slug, sub, storeId);
        lines.delete(requireLine(ctx, itemId));
        lines.flush();
        return dto(ctx);
    }

    @Transactional
    public CartDto keep(String slug, String sub, long storeId, long itemId, boolean keep) {
        Context ctx = context(slug, sub, storeId);
        CartLine line = requireLine(ctx, itemId);
        line.setKeep(keep);
        lines.saveAndFlush(line);
        return dto(ctx);
    }

    @Transactional
    public CartDto clear(String slug, String sub, long storeId) {
        Context ctx = context(slug, sub, storeId);
        lines.deleteOfCart(ctx.cart().getId());
        return dto(ctx);
    }

    @Transactional
    public CartDto bag(String slug, String sub, long storeId, boolean bagRequested) {
        Context ctx = context(slug, sub, storeId);
        ctx.cart().setBagRequested(bagRequested);
        carts.saveAndFlush(ctx.cart());
        return dto(ctx);
    }

    // ------------------------------------------------------------------ helpers

    private Context context(String slug, String sub, long storeId) {
        String shop = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        Membership m = sources.membership(shop, sub);
        StoreView store = sources.store(shop, storeId);
        Cart cart = carts.findByCustomerIdAndStoreId(m.customerId(), storeId).orElseGet(() -> {
            Cart n = new Cart();
            n.setCompanyId(m.companyId());
            n.setCustomerId(m.customerId());
            n.setStoreId(storeId);
            return carts.saveAndFlush(n);
        });
        return new Context(m, store, cart);
    }

    private CartLine requireLine(Context ctx, long itemId) {
        return lines.findByCartIdAndItemId(ctx.cart().getId(), itemId)
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_006));
    }

    private CartDto dto(Context ctx) {
        return dto(ctx, null);
    }

    private CartDto dto(Context ctx, String couponCode) {
        List<CartLine> rows = lines.findByCartIdOrderByIdAsc(ctx.cart().getId());
        Map<String, ShelfItem> live = sources.shelf(ctx.member().companyId(), ctx.store().warehouseId(),
                        rows.stream().map(CartLine::getItemId).toList()).stream()
                .collect(Collectors.toMap(ShelfItem::itemId, Function.identity(), (a, b) -> a));
        List<LineDto> out = rows.stream().map(r -> {
            ShelfItem i = live.get(String.valueOf(r.getItemId()));
            BigDecimal price = i == null ? null : i.salePrice();
            BigDecimal total = price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(r.getQuantity()));
            return new LineDto(String.valueOf(r.getItemId()), i == null ? null : i.name(), price, r.getQuantity(), total,
                    i == null ? null : i.purchaseLimitQty(), i != null && i.soldOut(), i != null, r.isKeep(), r.getCreatedDate());
        }).toList();
        List<Priced> priced = out.stream().map(l -> new Priced(l.lineTotal(), l.available())).toList();
        BigDecimal discount = BigDecimal.ZERO;
        String code = null;
        String error = null;
        if (couponCode != null) {
            code = com.terp.erp.order.coupon.CouponRules.normalize(couponCode);
            var r = coupons.preview(ctx.member().companyId(), ctx.member().customerId(), code, CartRules.subtotal(priced));
            if (r.ok()) {
                discount = r.discount();
            } else {
                error = r.error().getCode();
            }
        }
        Summary s = CartRules.summary(ctx.store(), priced, ctx.cart().isBagRequested(), discount);
        return new CartDto(ctx.store().id(), out, s.subtotal(), s.deliveryFee(), s.bagFee(), ctx.cart().isBagRequested(),
                s.total(), s.minOrderAmount(), s.minimumReached(), ctx.store().freeDeliveryThreshold(),
                ctx.store().orderableNow(), ctx.store().opensAt(), code, s.discount(), error);
    }
}
