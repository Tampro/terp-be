package com.terp.erp.order.ret;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.client.CartSourcesClient.StockLine;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.common.EReturnError;
import com.terp.erp.order.mileage.MileageLedger;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderLine;
import com.terp.erp.order.order.CustomerOrderLineRepository;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.order.order.OrderJournal;
import com.terp.erp.order.ret.ReturnDtos.LineDto;
import com.terp.erp.order.ret.ReturnDtos.PolicyDto;
import com.terp.erp.order.ret.ReturnDtos.RequestForm;
import com.terp.erp.order.ret.ReturnDtos.ReturnDto;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.TenantGuard;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Returns of delivered items: the customer asks, Back Office approves or rejects, then completes once the goods are back. */
@Service
@RequiredArgsConstructor
public class ReturnService {
    public static final String EVENT_RETURNED = "order.returned";
    public static final String SOURCE_TYPE = "SHOP_ORDER_RETURN";
    static final String STOCK_SOURCE = "ORDER_RETURN";
    private static final Set<EReturnStatus> OPEN = Set.of(EReturnStatus.REQUESTED, EReturnStatus.APPROVED);

    private final OrderReturnRepository returns;
    private final OrderReturnLineRepository returnLines;
    private final ReturnPolicyRepository policies;
    private final ReturnNumbers numbers;
    private final CustomerOrderRepository orders;
    private final CustomerOrderLineRepository orderLines;
    private final CartSourcesClient sources;
    private final MileageLedger mileage;
    private final OutboxPublisher outbox;
    private final PlatformTransactionManager txManager;
    private final Clock clock;

    // ------------------------------------------------------------------ customer

    /** The order row is locked so two requests on one order cannot both pass the quantity and open-return checks. */
    @Transactional
    public ReturnDto request(String slug, String sub, String orderNo, RequestForm form) {
        Membership m = sources.membership(norm(slug), sub);
        CustomerOrder found = orders.findByCompanyIdAndOrderNoAndCustomerId(m.companyId(), orderNo, m.customerId())
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        CustomerOrder o = orders.lockById(found.getId()).orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        ReturnRules.checkWindow(o, clock.instant());
        returns.findFirstByOrderIdAndStatusIn(o.getId(), OPEN).ifPresent(r -> {
            throw ApiException.badRequest(EReturnError.RET_005, r.getReturnNo());
        });
        List<CustomerOrderLine> lines = orderLines.findByOrderIdOrderByIdAsc(o.getId());
        ReturnRules.Quote q = ReturnRules.quote(o, lines, taken(o.getId()),
                form.lines().stream().map(l -> new ReturnRules.Requested(l.itemId(), l.quantity())).toList(),
                form.reason(), policy(o.getCompanyId()).getPickupFee(), mileage.earnedFor(o.getId()));
        OrderReturn r = new OrderReturn();
        r.setCompanyId(o.getCompanyId());
        r.setOrderId(o.getId());
        r.setOrderNo(o.getOrderNo());
        r.setStoreId(o.getStoreId());
        r.setCustomerId(o.getCustomerId());
        r.setReturnNo(numbers.next(o.getCompanyId(), LocalDate.now(clock.withZone(ReturnRules.ZONE))));
        r.setStatus(EReturnStatus.REQUESTED);
        r.setReason(form.reason());
        r.setNote(form.note() == null || form.note().isBlank() ? null : form.note().trim());
        r.setGoodsAmount(q.goods());
        r.setRefundShare(q.share());
        r.setPickupFee(q.fee());
        r.setRefundAmount(q.refund());
        r.setCostAmount(q.cost());
        r.setPointsBack(q.pointsBack());
        r.setEarnedBack(q.earnedBack());
        OrderReturn saved = returns.saveAndFlush(r);
        List<OrderReturnLine> rl = q.lines().stream().map(x -> {
            OrderReturnLine l = new OrderReturnLine();
            l.setReturnId(saved.getId());
            l.setOrderLineId(x.line().getId());
            l.setItemId(x.line().getItemId());
            l.setName(x.line().getName());
            l.setQuantity(x.quantity());
            l.setUnitPrice(x.line().getUnitPrice());
            l.setShare(x.share());
            l.setUnitCost(x.line().getUnitCost() == null ? BigDecimal.ZERO : x.line().getUnitCost());
            return l;
        }).toList();
        returnLines.saveAll(rl);
        return dto(saved, rl);
    }

    @Transactional(readOnly = true)
    public List<ReturnDto> mine(String slug, String sub, String orderNo) {
        Membership m = sources.membership(norm(slug), sub);
        CustomerOrder o = orders.findByCompanyIdAndOrderNoAndCustomerId(m.companyId(), orderNo, m.customerId())
                .orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        return returns.findByOrderIdOrderByIdDesc(o.getId()).stream().map(this::dto).toList();
    }

    // ------------------------------------------------------------------ Back Office

    @Transactional(readOnly = true)
    public Page<ReturnDto> list(Long requestedCompanyId, EReturnStatus status, Long storeId, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EReturnError.RET_008);
        return returns.search(company, status, storeId, pageable).map(this::dto);
    }

    @Transactional(readOnly = true)
    public ReturnDto get(Long id) {
        OrderReturn r = returns.findById(id).orElseThrow(() -> ApiException.notFound(EReturnError.RET_007));
        TenantGuard.assertSameCompany(r.getCompanyId(), EReturnError.RET_007);
        return dto(r);
    }

    @Transactional
    public ReturnDto approve(Long id, EReturnReason reason) {
        OrderReturn r = lock(id, EReturnStatus.REQUESTED);
        if (reason != null && reason != r.getReason()) {
            BigDecimal fee = reason.chargesFee() ? policy(r.getCompanyId()).getPickupFee() : BigDecimal.ZERO;
            r.setRefundAmount(ReturnRules.refund(r.getRefundShare(), reason, fee));
            r.setPickupFee(fee);
            r.setReason(reason);
        }
        r.setStatus(EReturnStatus.APPROVED);
        r.setApprovedAt(clock.instant());
        return dto(returns.saveAndFlush(r));
    }

    @Transactional
    public ReturnDto reject(Long id, String note) {
        OrderReturn r = lock(id, EReturnStatus.REQUESTED);
        r.setStatus(EReturnStatus.REJECTED);
        r.setRejectedAt(clock.instant());
        r.setRejectNote(note.trim());
        return dto(returns.saveAndFlush(r));
    }

    /**
     * Goods are back: stock in first (remote, idempotent on (ORDER_RETURN, return id)), then one local transaction —
     * COMPLETED, points, reversal journal and order.returned in the outbox. A stock failure leaves the return
     * APPROVED and a retry completes; it is never COMPLETED with the stock still out.
     */
    public ReturnDto complete(Long id) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        OrderReturn r = tx.execute(st -> {
            OrderReturn found = returns.findById(id).orElseThrow(() -> ApiException.notFound(EReturnError.RET_007));
            TenantGuard.assertSameCompany(found.getCompanyId(), EReturnError.RET_007);
            state(found, EReturnStatus.APPROVED);
            return found;
        });
        CustomerOrder o = orders.findById(r.getOrderId()).orElseThrow(() -> ApiException.notFound(EReturnError.RET_007));
        List<OrderReturnLine> lines = returnLines.findByReturnIdOrderByIdAsc(r.getId());
        sources.moveStock(r.getCompanyId(), r.getId(), lines.stream()
                .map(l -> new StockLine(l.getItemId(), o.getWarehouseId(), BigDecimal.valueOf(l.getQuantity()))).toList(),
                STOCK_SOURCE);
        return tx.execute(st -> {
            OrderReturn d = lock(id, EReturnStatus.APPROVED);
            d.setStatus(EReturnStatus.COMPLETED);
            d.setCompletedAt(clock.instant());
            mileage.giveBack(o, d.getPointsBack());
            d.setEarnedBack(mileage.clawBack(o, d.getEarnedBack()));
            ReturnRules.JournalAmounts a = ReturnRules.journal(d.getRefundShare(), d.getPickupFee(), d.getCostAmount());
            outbox.publish(d.getCompanyId(), OrderJournal.EVENT_REQUESTED, SOURCE_TYPE, d.getId(), Payload.of(
                    "sourceType", SOURCE_TYPE, "sourceId", d.getId(),
                    "date", LocalDate.ofInstant(d.getCompletedAt(), ReturnRules.ZONE), "reference", d.getReturnNo(),
                    "description", "Return " + d.getReturnNo() + " of Express order " + d.getOrderNo(),
                    "goodsAmount", a.goods(), "serviceAmount", a.service(), "taxAmount", a.tax(),
                    "costAmount", a.cost(), "actorSub", null));
            outbox.publish(d.getCompanyId(), EVENT_RETURNED, "ORDER", d.getOrderId(), Payload.of("orderId", d.getOrderId(),
                    "returnId", d.getId(), "refundAmount", d.getRefundAmount()));
            return dto(returns.saveAndFlush(d), lines);
        });
    }

    @Transactional(readOnly = true)
    public PolicyDto policyDto(Long requestedCompanyId) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EReturnError.RET_008);
        return policies.findById(company).map(p -> toDto(p, true)).orElseGet(() -> toDto(ReturnPolicy.defaults(company), false));
    }

    /** PUT is full replacement. */
    @Transactional
    public PolicyDto savePolicy(Long requestedCompanyId, BigDecimal pickupFee) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EReturnError.RET_008);
        ReturnPolicy p = policies.findById(company).orElseGet(() -> ReturnPolicy.defaults(company));
        p.setPickupFee(pickupFee);
        return toDto(policies.saveAndFlush(p), true);
    }

    // ------------------------------------------------------------------ helpers

    private ReturnPolicy policy(Long companyId) {
        return policies.findById(companyId).orElseGet(() -> ReturnPolicy.defaults(companyId));
    }

    private Map<Long, Integer> taken(Long orderId) {
        Map<Long, Integer> m = new HashMap<>();
        for (Object[] row : returnLines.takenByOrderLine(orderId)) {
            m.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return m;
    }

    private OrderReturn lock(Long id, EReturnStatus expected) {
        OrderReturn r = returns.lockById(id).orElseThrow(() -> ApiException.notFound(EReturnError.RET_007));
        TenantGuard.assertSameCompany(r.getCompanyId(), EReturnError.RET_007);
        state(r, expected);
        return r;
    }

    private static void state(OrderReturn r, EReturnStatus expected) {
        if (r.getStatus() != expected) {
            throw ApiException.badRequest(EReturnError.RET_006, r.getStatus().name());
        }
    }

    private static PolicyDto toDto(ReturnPolicy p, boolean saved) {
        return new PolicyDto(String.valueOf(p.getCompanyId()), p.getPickupFee(), ReturnPolicy.WINDOW_DAYS, saved);
    }

    private ReturnDto dto(OrderReturn r) {
        return dto(r, returnLines.findByReturnIdOrderByIdAsc(r.getId()));
    }

    private static ReturnDto dto(OrderReturn r, List<OrderReturnLine> lines) {
        return new ReturnDto(String.valueOf(r.getId()), r.getReturnNo(), String.valueOf(r.getOrderId()), r.getOrderNo(),
                String.valueOf(r.getStoreId()), r.getStatus(), r.getReason(), r.getNote(), r.getRejectNote(),
                r.getGoodsAmount(), r.getRefundShare(), r.getPickupFee(), r.getRefundAmount(), r.getPointsBack(),
                r.getEarnedBack(), r.getCreatedDate(), r.getApprovedAt(), r.getRejectedAt(), r.getCompletedAt(),
                lines.stream().map(l -> new LineDto(String.valueOf(l.getItemId()), l.getName(), l.getQuantity(),
                        l.getUnitPrice(), l.getShare())).toList());
    }

    private static String norm(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }
}
