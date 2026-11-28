package com.terp.erp.order.order;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.cart.Cart;
import com.terp.erp.order.cart.CartLine;
import com.terp.erp.order.cart.CartLineRepository;
import com.terp.erp.order.cart.CartRepository;
import com.terp.erp.order.cart.CartRules;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Address;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.client.CartSourcesClient.ShelfItem;
import com.terp.erp.order.client.CartSourcesClient.StockLine;
import com.terp.erp.order.client.CartSourcesClient.StoreView;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.common.FieldCipher;
import com.terp.erp.order.config.OrderProperties;
import com.terp.erp.order.order.OrderDtos.EPayOutcome;
import com.terp.erp.order.order.OrderDtos.LineDto;
import com.terp.erp.order.order.OrderDtos.OrderDto;
import com.terp.erp.order.order.OrderDtos.PlaceForm;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checkout (cart → PENDING_PAYMENT order with a snapshot), sandbox payment (stock out once → PAID) and cancel.
 * The member and the shop's company come from member-service by the token subject; the store from store-service.
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    // TODO(assumption): the chain's stores are in Korea; the order number uses the Korean date.
    private static final ZoneId ORDER_ZONE = ZoneId.of("Asia/Seoul");
    public static final String EVENT_PAID = "order.paid";

    private final CustomerOrderRepository orders;
    private final CustomerOrderLineRepository orderLines;
    private final CartRepository carts;
    private final CartLineRepository cartLines;
    private final CartSourcesClient sources;
    private final OrderNumbers numbers;
    private final FieldCipher cipher;
    private final OrderProperties props;
    private final PlatformTransactionManager txManager;
    private final OutboxPublisher outbox;
    private final com.terp.erp.order.coupon.CouponLedger coupons;
    private final Clock clock;

    // ------------------------------------------------------------------ checkout

    @Transactional
    public OrderDto place(String slug, String sub, long storeId, PlaceForm f) {
        return place(slug, sub, null, storeId, f);
    }

    /** @param email the customer's email from the token (may be null): kept encrypted for order / delivery emails. */
    @Transactional
    public OrderDto place(String slug, String sub, String email, long storeId, PlaceForm f) {
        String shop = norm(slug);
        Membership m = sources.membership(shop, sub);
        StoreView store = sources.store(shop, storeId);
        Cart cart = carts.findByCustomerIdAndStoreId(m.customerId(), storeId).orElse(null);
        List<CartLine> lines = cart == null ? List.of() : cartLines.findByCartIdOrderByIdAsc(cart.getId());
        if (lines.isEmpty()) {
            throw ApiException.badRequest(EOrderError.ORD_008);
        }
        orders.findFirstByCustomerIdAndStoreIdAndStatus(m.customerId(), storeId, EOrderStatus.PENDING_PAYMENT)
                .ifPresent(o -> {
                    throw ApiException.badRequest(EOrderError.ORD_014, o.getOrderNo());
                });
        Map<String, ShelfItem> live = sources.shelf(m.companyId(), store.warehouseId(),
                        lines.stream().map(CartLine::getItemId).toList()).stream()
                .collect(Collectors.toMap(ShelfItem::itemId, Function.identity(), (a, b) -> a));
        List<CartRules.Priced> priced = new ArrayList<>();
        for (CartLine l : lines) {
            ShelfItem i = live.get(String.valueOf(l.getItemId()));
            if (i == null || i.soldOut()) {
                throw ApiException.badRequest(EOrderError.ORD_009, i == null ? "#" + l.getItemId() : i.name());
            }
            priced.add(new CartRules.Priced(i.salePrice().multiply(BigDecimal.valueOf(l.getQuantity())), true));
        }
        com.terp.erp.order.coupon.CouponLedger.Applied coupon = f.couponCode() == null || f.couponCode().isBlank() ? null
                : coupons.lockAndCheck(m.companyId(), m.customerId(), f.couponCode(), CartRules.subtotal(priced));
        CartRules.Summary s = CartRules.summary(store, priced, cart.isBagRequested(),
                coupon == null ? BigDecimal.ZERO : coupon.discount());
        if (!s.minimumReached()) {
            throw ApiException.badRequest(EOrderError.ORD_010, s.minOrderAmount().stripTrailingZeros().toPlainString());
        }
        if (!store.orderableNow()) {
            throw ApiException.badRequest(EOrderError.ORD_011);
        }
        Address a = sources.address(shop, sub, f.addressId());
        if (!sources.serves(shop, storeId, a.lat(), a.lon())) {
            throw ApiException.badRequest(EOrderError.ORD_013);
        }
        CustomerOrder o = new CustomerOrder();
        o.setCompanyId(m.companyId());
        o.setCustomerId(m.customerId());
        o.setStoreId(storeId);
        o.setWarehouseId(store.warehouseId());
        o.setOrderNo(numbers.next(m.companyId(), LocalDate.now(clock.withZone(ORDER_ZONE))));
        o.setStatus(EOrderStatus.PENDING_PAYMENT);
        o.setSubstitution(f.substitution());
        o.setSubtotal(s.subtotal());
        o.setDeliveryFee(s.deliveryFee());
        o.setBagFee(s.bagFee());
        o.setTotal(s.total());
        o.setDiscount(s.discount());
        if (coupon != null) {
            o.setCouponId(coupon.coupon().getId());
            o.setCouponCode(coupon.coupon().getCode());
        }
        o.setStoreName(store.name());
        o.setRecipientName(a.recipientName());
        o.setPhoneEnc(cipher.encrypt(a.phone()));
        o.setEmailEnc(email == null || email.isBlank() ? null : cipher.encrypt(email.trim()));
        o.setPostcode(a.postcode());
        o.setAddress1(a.address1());
        o.setAddress2(a.address2());
        o.setLat(a.lat());
        o.setLon(a.lon());
        o.setDeliveryNote(a.deliveryNote());
        o.setNote(f.note() == null || f.note().isBlank() ? null : f.note().trim());
        CustomerOrder saved = orders.saveAndFlush(o);
        if (coupon != null) {
            coupons.reserve(coupon.coupon(), m.customerId(), saved.getId());
        }
        List<CustomerOrderLine> snapshot = new ArrayList<>();
        for (CartLine l : lines) {
            ShelfItem i = live.get(String.valueOf(l.getItemId()));
            CustomerOrderLine ol = new CustomerOrderLine();
            ol.setOrderId(saved.getId());
            ol.setItemId(l.getItemId());
            ol.setName(i.name());
            ol.setSku(i.sku());
            ol.setUnitPrice(i.salePrice());
            ol.setQuantity(l.getQuantity());
            ol.setLineTotal(i.salePrice().multiply(BigDecimal.valueOf(l.getQuantity())));
            snapshot.add(ol);
        }
        orderLines.saveAll(snapshot);
        return dto(saved, snapshot, false);
    }

    // ------------------------------------------------------------------ payment (sandbox)

    /**
     * Not one transaction on purpose: the stock move is a remote call, and a refused move must leave the order in
     * PAYMENT_FAILED (committed) before answering 400 ORD_015. Retrying an APPROVED payment after a timeout is safe:
     * inventory applies (ORDER, order id) once.
     */
    public OrderDto pay(String slug, String sub, String orderNo, EPayOutcome outcome) {
        if (!props.paymentSandbox()) {
            throw ApiException.notFound(EOrderError.ORD_018);
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CustomerOrder o = tx.execute(st -> {
            CustomerOrder found = mine(slug, sub, orderNo);
            if (found.getStatus() != EOrderStatus.PENDING_PAYMENT) {
                throw ApiException.badRequest(EOrderError.ORD_016, found.getStatus().name());
            }
            return found;
        });
        if (outcome == EPayOutcome.DECLINED) {
            return tx.execute(st -> fail(o.getId(), "DECLINED"));
        }
        List<CustomerOrderLine> lines = orderLines.findByOrderIdOrderByIdAsc(o.getId());
        Map<Long, BigDecimal> costs;
        try {
            costs = sources.moveStock(o.getCompanyId(), o.getId(), lines.stream()
                    .map(l -> new StockLine(l.getItemId(), o.getWarehouseId(), BigDecimal.valueOf(-l.getQuantity()))).toList());
        } catch (ApiException e) {
            if (e.getErrorCode() == EOrderError.ORD_015) {
                tx.executeWithoutResult(st -> fail(o.getId(), "OUT_OF_STOCK"));
            }
            throw e;
        }
        return tx.execute(st -> {
            CustomerOrder paid = orders.findById(o.getId()).orElseThrow();
            paid.setStatus(EOrderStatus.PAID);
            paid.setPaidAt(clock.instant());
            lines.forEach(l -> l.setUnitCost(costs.getOrDefault(l.getItemId(), BigDecimal.ZERO)));
            orderLines.saveAll(lines);
            requestJournal(paid, lines);
            announcePaid(paid);
            orders.saveAndFlush(paid);
            carts.findByCustomerIdAndStoreId(paid.getCustomerId(), paid.getStoreId()).ifPresent(cart -> {
                Set<Long> bought = lines.stream().map(CustomerOrderLine::getItemId).collect(Collectors.toSet());
                cartLines.findByCartIdOrderByIdAsc(cart.getId()).stream().filter(l -> bought.contains(l.getItemId()))
                        .forEach(cartLines::delete);
            });
            return dto(paid, lines, false);
        });
    }

    /**
     * The journal request is written in the transaction of the PAID status (outbox): published only if the payment
     * commits, never lost if accounting or the broker is down. No personal data in the payload.
     */
    private void requestJournal(CustomerOrder o, List<CustomerOrderLine> lines) {
        OrderJournal.Amounts a = OrderJournal.amounts(o, lines);
        outbox.publish(o.getCompanyId(), OrderJournal.EVENT_REQUESTED, OrderJournal.SOURCE_TYPE, o.getId(), Payload.of(
                "sourceType", OrderJournal.SOURCE_TYPE, "sourceId", o.getId(),
                "date", LocalDate.ofInstant(o.getPaidAt(), ORDER_ZONE), "reference", o.getOrderNo(),
                "description", "Express order " + o.getOrderNo(), "goodsAmount", a.goods(), "serviceAmount", a.service(),
                "taxAmount", a.tax(), "costAmount", a.cost(), "actorSub", null));
        o.setJournalStatus(EJournalStatus.PENDING);
    }

    /** delivery-service creates the delivery from it; ids only — the address is read back on demand (no PII on the broker). */
    private void announcePaid(CustomerOrder o) {
        outbox.publish(o.getCompanyId(), EVENT_PAID, "ORDER", o.getId(), Payload.of("orderId", o.getId(),
                "orderNo", o.getOrderNo(), "storeId", o.getStoreId(), "warehouseId", o.getWarehouseId()));
    }

    /** Internal read for notification-service: who to email and what to say; another company's → ORD_018. */
    @Transactional(readOnly = true)
    public NotificationFactsDto notificationFacts(long companyId, long id) {
        CustomerOrder o = orders.findById(id).filter(x -> x.getCompanyId() != null && x.getCompanyId() == companyId)
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        return new NotificationFactsDto(o.getOrderNo(), o.getStoreName(), o.getRecipientName(),
                o.getEmailEnc() == null ? null : cipher.decrypt(o.getEmailEnc()), o.getTotal(), o.getStatus(),
                o.getDeliveryStatus());
    }

    /** Internal read for delivery-service: the order's snapshot with the plain phone; another company's → ORD_018. */
    @Transactional(readOnly = true)
    public InternalOrderDto internal(long companyId, long id) {
        CustomerOrder o = orders.findById(id).filter(x -> x.getCompanyId() != null && x.getCompanyId() == companyId)
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        return new InternalOrderDto(String.valueOf(o.getId()), o.getOrderNo(), o.getStatus(), o.getStoreName(),
                o.getRecipientName(), cipher.decrypt(o.getPhoneEnc()), o.getPostcode(), o.getAddress1(), o.getAddress2(),
                o.getLat(), o.getLon(), o.getDeliveryNote(), o.getNote(), o.getTotal(),
                orderLines.findByOrderIdOrderByIdAsc(o.getId()).stream()
                        .map(l -> new InternalOrderDto.Line(l.getName(), l.getQuantity())).toList());
    }

    private OrderDto fail(long orderId, String reason) {
        CustomerOrder o = orders.findById(orderId).orElseThrow();
        o.setStatus(EOrderStatus.PAYMENT_FAILED);
        o.setFailureReason(reason);
        orders.saveAndFlush(o);
        return dto(o, orderLines.findByOrderIdOrderByIdAsc(o.getId()), false);
    }

    // ------------------------------------------------------------------ cancel, read

    /** Before payment only (FR-ORD-008: a paid Magic-Now order is prepared at once; refunds are CS work, P6). */
    @Transactional
    public OrderDto cancel(String slug, String sub, String orderNo) {
        CustomerOrder o = mine(slug, sub, orderNo);
        if (o.getStatus() == EOrderStatus.PAID) {
            throw ApiException.badRequest(EOrderError.ORD_017);
        }
        if (o.getStatus() == EOrderStatus.PENDING_PAYMENT || o.getStatus() == EOrderStatus.PAYMENT_FAILED) {
            o.setStatus(EOrderStatus.CANCELLED);
            o.setCancelledAt(clock.instant());
            coupons.release(o.getId());
            orders.saveAndFlush(o);
        }
        return dto(o, orderLines.findByOrderIdOrderByIdAsc(o.getId()), false);
    }

    @Transactional(readOnly = true)
    public OrderDto get(String slug, String sub, String orderNo) {
        CustomerOrder o = mine(slug, sub, orderNo);
        return dto(o, orderLines.findByOrderIdOrderByIdAsc(o.getId()), false);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> list(String slug, String sub, Pageable pageable) {
        Membership m = sources.membership(norm(slug), sub);
        return orders.findByCustomerIdAndCompanyIdOrderByIdDesc(m.customerId(), m.companyId(), pageable)
                .map(o -> dto(o, orderLines.findByOrderIdOrderByIdAsc(o.getId()), false));
    }

    /** Back Office view (masked phone). */
    OrderDto staffDto(CustomerOrder o) {
        return dto(o, orderLines.findByOrderIdOrderByIdAsc(o.getId()), true);
    }

    private CustomerOrder mine(String slug, String sub, String orderNo) {
        Membership m = sources.membership(norm(slug), sub);
        return orders.findByCompanyIdAndOrderNoAndCustomerId(m.companyId(), orderNo, m.customerId())
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
    }

    private OrderDto dto(CustomerOrder o, List<CustomerOrderLine> lines, boolean masked) {
        String phone = cipher.decrypt(o.getPhoneEnc());
        return new OrderDto(String.valueOf(o.getId()), o.getOrderNo(), o.getStatus(), o.getFailureReason(),
                String.valueOf(o.getStoreId()), o.getStoreName(), o.getSubstitution(),
                lines.stream().map(l -> new LineDto(String.valueOf(l.getItemId()), l.getName(), l.getSku(), l.getUnitPrice(),
                        l.getQuantity(), l.getLineTotal())).toList(),
                o.getSubtotal(), o.getDeliveryFee(), o.getBagFee(), o.getTotal(), o.getRecipientName(),
                masked ? mask(phone) : phone, o.getPostcode(), o.getAddress1(), o.getAddress2(), o.getLat(), o.getLon(),
                o.getDeliveryNote(), o.getNote(), o.getCreatedDate(), o.getPaidAt(), o.getCancelledAt(),
                masked ? o.getJournalStatus() : null, masked ? o.getJournalEntryNo() : null,
                masked ? o.getJournalError() : null, o.getDeliveryStatus(), o.getDeliveryStatusAt(),
                email(o, masked), o.getCouponCode(), o.getDiscount());
    }

    private String email(CustomerOrder o, boolean masked) {
        String e = o.getEmailEnc() == null ? null : cipher.decrypt(o.getEmailEnc());
        return masked ? maskEmail(e) : e;
    }

    /** hong@example.com → h***@example.com (staff never sees the full address). */
    static String maskEmail(String e) {
        if (e == null) {
            return null;
        }
        int at = e.indexOf('@');
        return at < 1 ? "***" : e.charAt(0) + "***" + e.substring(at);
    }

    /** 01012345678 → 010-****-5678 (staff never sees the full number). */
    static String mask(String digits) {
        if (digits == null || digits.length() < 7) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    private static String norm(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }
}
