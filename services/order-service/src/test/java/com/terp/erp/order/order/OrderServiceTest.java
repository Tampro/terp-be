package com.terp.erp.order.order;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.order.cart.Cart;
import com.terp.erp.order.cart.CartLine;
import com.terp.erp.order.cart.CartLineRepository;
import com.terp.erp.order.cart.CartRepository;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Address;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.client.CartSourcesClient.ShelfItem;
import com.terp.erp.order.client.CartSourcesClient.StoreView;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.common.FieldCipher;
import com.terp.erp.order.common.FieldCipherTest;
import com.terp.erp.order.config.OrderProperties;
import com.terp.erp.order.coupon.Coupon;
import com.terp.erp.order.order.OrderDtos.EPayOutcome;
import com.terp.erp.order.order.OrderDtos.PlaceForm;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final CustomerOrderLineRepository orderLines = mock(CustomerOrderLineRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final CartLineRepository cartLines = mock(CartLineRepository.class);
    private final CartSourcesClient sources = mock(CartSourcesClient.class);
    private final OrderNumbers numbers = mock(OrderNumbers.class);
    private final FieldCipher cipher = FieldCipherTest.cipher();
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final com.terp.erp.order.coupon.CouponLedger coupons = mock(com.terp.erp.order.coupon.CouponLedger.class);
    private OrderService service;
    private final List<CartLine> lines = new ArrayList<>();
    private final List<CustomerOrder> saved = new ArrayList<>();
    private List<CustomerOrderLine> placedLines = List.of();
    private StoreView store = store(true);

    @BeforeEach
    void setUp() {
        service = service(true);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(sources.membership("express", "sub-1")).thenReturn(new Membership(5L, 10L));
        when(sources.store(eq("express"), eq(7L))).thenAnswer(a -> store);
        Cart cart = new Cart();
        cart.setId(1L);
        when(carts.findByCustomerIdAndStoreId(5L, 7L)).thenReturn(Optional.of(cart));
        when(cartLines.findByCartIdOrderByIdAsc(1L)).thenAnswer(a -> List.copyOf(lines));
        when(orders.findFirstByCustomerIdAndStoreIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(sources.shelf(eq(10L), eq(245L), anyCollection())).thenAnswer(a -> ((java.util.Collection<Long>) a.getArgument(2))
                .stream().filter(id -> id != 404L).map(id -> new ShelfItem(String.valueOf(id), "Item " + id, "S" + id,
                        BigDecimal.valueOf(6000), 10, id == 3L)).toList());
        when(sources.address("express", "sub-1", 12L)).thenReturn(new Address("Hong", "01012345678", "08502", "1 Test-ro", null,
                new BigDecimal("37.4"), new BigDecimal("126.8"), null));
        when(sources.serves(eq("express"), eq(7L), any(), any())).thenReturn(true);
        when(numbers.next(10L, LocalDate.of(2026, 11, 28))).thenReturn("HE-20261128-000001");
        when(orders.saveAndFlush(any())).thenAnswer(a -> {
            CustomerOrder o = a.getArgument(0);
            if (o.getId() == null) {
                o.setId(100L);
                saved.add(o);
            }
            return o;
        });
        when(orders.findById(100L)).thenAnswer(a -> saved.stream().findFirst());
        when(sources.moveStock(anyLong(), anyLong(), any())).thenReturn(java.util.Map.of(1L, new BigDecimal("4000")));
        when(orderLines.findByOrderIdOrderByIdAsc(100L)).thenAnswer(a -> placedLines);
    }

    private OrderService service(boolean sandbox) {
        OrderProperties props = new OrderProperties("u", "u", "u", "u", "u", "u", FieldCipherTest.KEY, sandbox, null, null, null, null);
        return new OrderService(orders, orderLines, carts, cartLines, sources, numbers, cipher, props, tx, outbox, coupons,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void placeSnapshotsPricesFeesAndAnEncryptedPhone() {
        line(1L, 2);
        OrderDtos.OrderDto d = service.place("Express", "sub-1", 7L, new PlaceForm(12L, ESubstitution.ALLOW, " Ring "));
        assertThat(d.orderNo()).isEqualTo("HE-20261128-000001");
        assertThat(d.status()).isEqualTo(EOrderStatus.PENDING_PAYMENT);
        assertThat(d.subtotal()).isEqualByComparingTo("12000");
        assertThat(d.deliveryFee()).isEqualByComparingTo("3000");
        assertThat(d.total()).isEqualByComparingTo("15000");
        assertThat(d.phone()).isEqualTo("01012345678");
        assertThat(saved.get(0).getPhoneEnc()).doesNotContain("01012345678");
        assertThat(d.note()).isEqualTo("Ring");
    }

    @Test
    void placeRefusals() {
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_008);
        line(404L, 1);
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_009);
        lines.clear();
        line(3L, 2);
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_009); // sold out
        lines.clear();
        line(1L, 1);                                                                           // 6,000 < 10,000
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_010);
        lines.clear();
        line(1L, 2);
        store = store(false);
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_011);
        store = store(true);
        when(sources.serves(eq("express"), eq(7L), any(), any())).thenReturn(false);
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_013);
        CustomerOrder pending = new CustomerOrder();
        pending.setOrderNo("HE-20261128-000009");
        when(orders.findFirstByCustomerIdAndStoreIdAndStatus(5L, 7L, EOrderStatus.PENDING_PAYMENT)).thenReturn(Optional.of(pending));
        assertCode(() -> service.place("express", "sub-1", 7L, form()), EOrderError.ORD_014);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void payApprovedMovesStockOnceAndMarksPaid() {
        CustomerOrder o = placed();
        OrderDtos.OrderDto d = service.pay("express", "sub-1", o.getOrderNo(), EPayOutcome.APPROVED);
        assertThat(d.status()).isEqualTo(EOrderStatus.PAID);
        assertThat(d.paidAt()).isEqualTo(NOW);
        verify(sources).moveStock(eq(10L), eq(100L), any());
        assertCode(() -> service.pay("express", "sub-1", o.getOrderNo(), EPayOutcome.APPROVED), EOrderError.ORD_016);
        assertCode(() -> service.cancel("express", "sub-1", o.getOrderNo()), EOrderError.ORD_017);
    }

    @Test
    void payApprovedRequestsTheJournalOnceWithNetAmountsVatAndCost() {
        CustomerOrder o = placed();
        service.pay("express", "sub-1", o.getOrderNo(), EPayOutcome.APPROVED);
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> c = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(outbox).publish(eq(10L), eq("commerce.journal.requested"), eq("SHOP_ORDER"), eq(100L), c.capture());
        java.util.Map<String, Object> p = c.getValue();
        // goods 12,000 incl. VAT → 10,909 + 1,091; delivery 3,000 → 2,727 + 273; cost 2 × 4,000
        assertThat((BigDecimal) p.get("goodsAmount")).isEqualByComparingTo("10909");
        assertThat((BigDecimal) p.get("serviceAmount")).isEqualByComparingTo("2727");
        assertThat((BigDecimal) p.get("taxAmount")).isEqualByComparingTo("1364");
        assertThat((BigDecimal) p.get("costAmount")).isEqualByComparingTo("8000");
        assertThat(p.get("reference")).isEqualTo("HE-20261128-000001");
        assertThat(p.keySet()).doesNotContain("phone", "recipientName", "address1");
        assertThat(o.getJournalStatus()).isEqualTo(EJournalStatus.PENDING);
        assertThat(placedLines.get(0).getUnitCost()).isEqualByComparingTo("4000");
    }

    @Test
    void payApprovedAnnouncesThePaidOrderWithIdsOnly() {
        CustomerOrder o = placed();
        service.pay("express", "sub-1", o.getOrderNo(), EPayOutcome.APPROVED);
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> c = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(outbox).publish(eq(10L), eq("order.paid"), eq("ORDER"), eq(100L), c.capture());
        assertThat(c.getValue().keySet()).containsExactlyInAnyOrder("orderId", "orderNo", "storeId", "warehouseId");
        assertThat(c.getValue().get("orderNo")).isEqualTo("HE-20261128-000001");
    }

    @Test
    void theEmailIsKeptEncryptedPlainForTheCustomerMaskedForStaff() {
        line(1L, 2);
        OrderDtos.OrderDto d = service.place("express", "sub-1", " hong@example.com ", 7L, form());
        assertThat(d.email()).isEqualTo("hong@example.com");
        CustomerOrder o = saved.get(0);
        assertThat(o.getEmailEnc()).doesNotContain("hong");
        assertThat(OrderService.maskEmail("hong@example.com")).isEqualTo("h***@example.com");
        assertThat(service.notificationFacts(10L, 100L).email()).isEqualTo("hong@example.com");
        assertCode(() -> service.notificationFacts(11L, 100L), EOrderError.ORD_018);
        saved.clear();
        lines.clear();
        line(1L, 2);
        assertThat(service.place("express", "sub-1", null, 7L, form()).email()).isNull();
    }

    @Test
    void internalReadGivesThePlainPhoneOnlyToTheOrdersCompany() {
        CustomerOrder o = placed();
        assertThat(service.internal(10L, 100L).phone()).isEqualTo("01012345678");
        assertCode(() -> service.internal(11L, 100L), EOrderError.ORD_018);
    }

    @Test
    void outOfStockLeavesTheOrderFailedAndDeclinedToo() {
        CustomerOrder o = placed();
        doThrow(ApiException.badRequest(EOrderError.ORD_015)).when(sources).moveStock(anyLong(), anyLong(), any());
        assertCode(() -> service.pay("express", "sub-1", o.getOrderNo(), EPayOutcome.APPROVED), EOrderError.ORD_015);
        assertThat(o.getStatus()).isEqualTo(EOrderStatus.PAYMENT_FAILED);
        assertThat(o.getFailureReason()).isEqualTo("OUT_OF_STOCK");
        assertThat(service.cancel("express", "sub-1", o.getOrderNo()).status()).isEqualTo(EOrderStatus.CANCELLED);
        CustomerOrder p = placed();
        p.setStatus(EOrderStatus.PENDING_PAYMENT);
        assertThat(service.pay("express", "sub-1", p.getOrderNo(), EPayOutcome.DECLINED).failureReason()).isEqualTo("DECLINED");
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void sandboxOffHidesThePayEndpoint() {
        assertCode(() -> service(false).pay("express", "sub-1", "HE-x", EPayOutcome.APPROVED), EOrderError.ORD_018);
    }

    @Test
    void phoneIsMaskedForStaffAndNumbersArePadded() {
        assertThat(OrderService.mask("01012345678")).isEqualTo("010-****-5678");
        assertThat(OrderService.mask("123")).isEqualTo("****");
        assertThat(OrderNumbers.format(LocalDate.of(2026, 11, 28), 42)).isEqualTo("HE-20261128-000042");
    }

    @Test
    void expiryJobUsesTheFifteenMinuteWindow() {
        OrderExpiryJob job = new OrderExpiryJob(orders, coupons, tx, Clock.fixed(NOW, ZoneOffset.UTC));
        CustomerOrder pending = new CustomerOrder();
        pending.setId(1L);
        pending.setStatus(EOrderStatus.PENDING_PAYMENT);
        CustomerOrder paidMeanwhile = new CustomerOrder();
        paidMeanwhile.setId(2L);
        paidMeanwhile.setStatus(EOrderStatus.PAID);
        when(orders.idsToExpire(NOW.minus(OrderExpiryJob.PAYMENT_WINDOW))).thenReturn(List.of(1L, 2L));
        when(orders.findById(1L)).thenReturn(Optional.of(pending));
        when(orders.findById(2L)).thenReturn(Optional.of(paidMeanwhile));
        assertThat(job.run()).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(EOrderStatus.EXPIRED);
        assertThat(paidMeanwhile.getStatus()).isEqualTo(EOrderStatus.PAID);
        verify(coupons).release(1L);
        verify(coupons, never()).release(2L);
    }

    @Test
    void aCouponIsCheckedReservedSnapshottedAndGivenBackOnCancel() {
        line(1L, 4);                                                            // 24,000 of goods
        Coupon c = new Coupon();
        c.setId(9L);
        c.setCode("SAVE");
        when(coupons.lockAndCheck(10L, 5L, "save", BigDecimal.valueOf(24000)))
                .thenReturn(new com.terp.erp.order.coupon.CouponLedger.Applied(c, BigDecimal.valueOf(3000)));
        OrderDtos.OrderDto d = service.place("express", "sub-1", 7L, new PlaceForm(12L, ESubstitution.ALLOW, null, "save"));
        assertThat(d.couponCode()).isEqualTo("SAVE");
        assertThat(d.discount()).isEqualByComparingTo("3000");
        assertThat(d.subtotal()).isEqualByComparingTo("24000");
        assertThat(d.total()).isEqualByComparingTo("24000");                     // 21,000 + delivery 3,000
        verify(coupons).reserve(c, 5L, 100L);
        CustomerOrder o = saved.get(0);
        OrderJournal.Amounts j = OrderJournal.amounts(o, placedLinesOf(o));
        assertThat(j.goods()).isEqualByComparingTo("19091");                     // 21,000 net of the discount, less VAT 1,909
        assertThat(j.goods().add(j.service()).add(j.tax())).isEqualByComparingTo("24000");
        when(orders.findByCompanyIdAndOrderNoAndCustomerId(10L, o.getOrderNo(), 5L)).thenReturn(Optional.of(o));
        service.cancel("express", "sub-1", o.getOrderNo());
        verify(coupons).release(100L);
    }

    @Test
    void aRefusedCouponStopsThePlacement() {
        line(1L, 4);
        when(coupons.lockAndCheck(10L, 5L, "late", BigDecimal.valueOf(24000)))
                .thenThrow(ApiException.badRequest(com.terp.erp.order.common.ECouponError.CPN_008));
        assertCode(() -> service.place("express", "sub-1", 7L, new PlaceForm(12L, ESubstitution.ALLOW, null, "late")),
                com.terp.erp.order.common.ECouponError.CPN_008);
        verify(orders, never()).saveAndFlush(any());
    }

    private List<CustomerOrderLine> placedLinesOf(CustomerOrder o) {
        org.mockito.ArgumentCaptor<List<CustomerOrderLine>> c = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(orderLines, org.mockito.Mockito.atLeastOnce()).saveAll(c.capture());
        return c.getValue();
    }

    private CustomerOrder placed() {
        saved.clear();
        lines.clear();
        line(1L, 2);
        service.place("express", "sub-1", 7L, form());
        CustomerOrder o = saved.get(0);
        org.mockito.ArgumentCaptor<List<CustomerOrderLine>> c = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(orderLines, org.mockito.Mockito.atLeastOnce()).saveAll(c.capture());
        placedLines = c.getValue();
        when(orders.findByCompanyIdAndOrderNoAndCustomerId(10L, o.getOrderNo(), 5L)).thenReturn(Optional.of(o));
        return o;
    }

    private void line(long itemId, int qty) {
        CartLine l = new CartLine();
        l.setItemId(itemId);
        l.setQuantity(qty);
        lines.add(l);
    }

    private static PlaceForm form() {
        return new PlaceForm(12L, ESubstitution.DENY, null);
    }

    private static StoreView store(boolean open) {
        return new StoreView("7", "Express", 245L, BigDecimal.valueOf(3000), BigDecimal.valueOf(30000), BigDecimal.valueOf(20000),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(200), open, null);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
