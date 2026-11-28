package com.terp.erp.order.cart;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.client.CartSourcesClient.ShelfItem;
import com.terp.erp.order.client.CartSourcesClient.StoreView;
import com.terp.erp.order.common.EOrderError;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {
    private final CartRepository carts = mock(CartRepository.class);
    private final CartLineRepository lines = mock(CartLineRepository.class);
    private final CartSourcesClient sources = mock(CartSourcesClient.class);
    private final com.terp.erp.order.coupon.CouponLedger coupons = mock(com.terp.erp.order.coupon.CouponLedger.class);
    private final CartService service = new CartService(carts, lines, sources, coupons);
    private final List<CartLine> rows = new ArrayList<>();
    private final Cart cart = new Cart();

    @BeforeEach
    void setUp() {
        cart.setId(1L);
        when(sources.membership("express", "sub-1")).thenReturn(new Membership(5L, 10L));
        when(sources.store("express", 7L)).thenReturn(new StoreView("7", "Express", 245L, BigDecimal.valueOf(3000),
                BigDecimal.valueOf(30000), BigDecimal.valueOf(20000), BigDecimal.valueOf(10000), BigDecimal.valueOf(200), true, null));
        when(carts.findByCustomerIdAndStoreId(5L, 7L)).thenReturn(Optional.of(cart));
        when(lines.findByCartIdOrderByIdAsc(1L)).thenAnswer(a -> List.copyOf(rows));
        when(lines.countByCartId(1L)).thenAnswer(a -> (long) rows.size());
        when(lines.findByCartIdAndItemId(eq(1L), anyLong())).thenAnswer(a ->
                rows.stream().filter(r -> r.getItemId().equals(a.getArgument(1))).findFirst());
        when(lines.saveAndFlush(any())).thenAnswer(a -> {
            CartLine l = a.getArgument(0);
            if (!rows.contains(l)) {
                rows.add(l);
            }
            return l;
        });
        when(sources.shelf(eq(10L), eq(245L), anyCollection())).thenAnswer(a -> {
            Collection<Long> ids = a.getArgument(2);
            return ids.stream().filter(id -> id != 404L)
                    .map(id -> new ShelfItem(String.valueOf(id), "Item " + id, "S" + id, BigDecimal.valueOf(5990), 10, id == 3L))
                    .toList();
        });
    }

    @Test
    void putCreatesThenReplacesTheQuantityAndPricesLive() {
        service.setQuantity("express", "sub-1", 7L, 1L, 2);
        CartService.CartDto d = service.setQuantity("Express ", "sub-1", 7L, 1L, 3);
        assertThat(d.lines()).hasSize(1);
        assertThat(d.lines().get(0).quantity()).isEqualTo(3);
        assertThat(d.subtotal()).isEqualByComparingTo("17970");
        assertThat(d.deliveryFee()).isEqualByComparingTo("3000");
        assertThat(d.minimumReached()).isTrue();
    }

    @Test
    void overTheLimitNotOnSaleOrTheHundredFirstLineAreRefused() {
        assertCode(() -> service.setQuantity("express", "sub-1", 7L, 1L, 11), EOrderError.ORD_002);
        assertCode(() -> service.setQuantity("express", "sub-1", 7L, 404L, 1), EOrderError.ORD_003);
        for (long i = 1000; i < 1100; i++) {
            CartLine l = new CartLine();
            l.setItemId(i);
            rows.add(l);
        }
        assertCode(() -> service.setQuantity("express", "sub-1", 7L, 2L, 1), EOrderError.ORD_004);
    }

    @Test
    void aLineNoLongerOnSaleIsUnavailableAndOutOfTheTotal() {
        service.setQuantity("express", "sub-1", 7L, 1L, 1);
        CartLine gone = new CartLine();
        gone.setItemId(404L);
        gone.setQuantity(5);
        rows.add(gone);
        CartService.CartDto d = service.view("express", "sub-1", 7L);
        assertThat(d.lines()).extracting(CartService.LineDto::available).containsExactly(true, false);
        assertThat(d.subtotal()).isEqualByComparingTo("5990");
    }

    @Test
    void removeUnknownLineIsOrd006AndClearDeletesTheCartLines() {
        assertCode(() -> service.remove("express", "sub-1", 7L, 99L), EOrderError.ORD_006);
        service.clear("express", "sub-1", 7L);
        verify(lines).deleteOfCart(1L);
    }

    @Test
    void notAMemberOrAStoreOfAnotherShopStopsBeforeAnyCart() {
        when(sources.membership("other", "sub-1")).thenThrow(ApiException.notFound(EOrderError.ORD_001));
        assertCode(() -> service.view("other", "sub-1", 7L), EOrderError.ORD_001);
        when(sources.store("express", 8L)).thenThrow(ApiException.notFound(EOrderError.ORD_005));
        assertCode(() -> service.view("express", "sub-1", 8L), EOrderError.ORD_005);
    }

    @Test
    void cleanupRemovesLinesOlderThanNinetyDaysOnly() {
        Instant now = Instant.parse("2026-11-28T04:00:00Z");
        CartCleanupJob job = new CartCleanupJob(lines, Clock.fixed(now, ZoneOffset.UTC));
        when(lines.deleteStale(now.minus(CartCleanupJob.KEEP_FOR))).thenReturn(3);
        assertThat(job.run()).isEqualTo(3);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }

    @Test
    void aCouponPreviewDiscountsOrExplainsItsRefusal() {
        CartLine l = new CartLine();
        l.setCartId(1L);
        l.setItemId(1L);
        l.setQuantity(4);
        rows.add(l);
        when(sources.shelf(eq(10L), eq(245L), anyCollection())).thenReturn(List.of(new CartSourcesClient.ShelfItem("1", "Egg",
                "S1", BigDecimal.valueOf(6000), 10, false)));
        when(coupons.preview(10L, 5L, "SAVE", BigDecimal.valueOf(24000)))
                .thenReturn(new com.terp.erp.order.coupon.CouponRules.Result(BigDecimal.valueOf(5000), null, null));
        CartService.CartDto d = service.view("express", "sub-1", 7L, " save ");
        assertThat(d.couponCode()).isEqualTo("SAVE");
        assertThat(d.discount()).isEqualByComparingTo("5000");
        assertThat(d.minimumReached()).isTrue();                                 // 19,000 after the discount >= first-order 10,000
        assertThat(d.total()).isEqualByComparingTo("22000");                     // 19,000 + delivery 3,000
        when(coupons.preview(10L, 5L, "GONE", BigDecimal.valueOf(24000))).thenReturn(new com.terp.erp.order.coupon.CouponRules.Result(
                BigDecimal.ZERO, com.terp.erp.order.common.ECouponError.CPN_007, null));
        CartService.CartDto bad = service.view("express", "sub-1", 7L, "gone");
        assertThat(bad.couponError()).isEqualTo("CPN_007");
        assertThat(bad.discount()).isEqualByComparingTo("0");
    }
}
