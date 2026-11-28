package com.terp.erp.order.ret;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EReturnError;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place that prices a return (REQ-04). Each returned unit is refunded what the customer paid for it: its
 * goods after the coupon and points, spread pro rata over the order's goods; a change of mind pays the pick-up fee.
 * Delivery and bag fees are never refunded. Points spent come back and points earned are taken back in the same
 * proportion (rounded down).
 */
public final class ReturnRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public record Requested(long itemId, int quantity) {
    }

    public record LineQuote(CustomerOrderLine line, int quantity, BigDecimal goods, BigDecimal share) {
    }

    public record Quote(List<LineQuote> lines, BigDecimal goods, BigDecimal share, BigDecimal fee, BigDecimal refund,
                        BigDecimal cost, int pointsBack, int earnedBack) {
    }

    /** Journal amounts: VAT = x / 11 (prices include 10 %), same rounding as SHOP_ORDER. */
    public record JournalAmounts(BigDecimal goods, BigDecimal service, BigDecimal tax, BigDecimal cost) {
    }

    private ReturnRules() {
    }

    /** Delivered and within the window: the Seoul calendar day of delivery + WINDOW_DAYS, inclusive. */
    public static void checkWindow(CustomerOrder o, Instant now) {
        if (!"DELIVERED".equals(o.getDeliveryStatus()) || o.getDeliveryStatusAt() == null) {
            throw ApiException.badRequest(EReturnError.RET_001);
        }
        LocalDate last = LocalDate.ofInstant(o.getDeliveryStatusAt(), ZONE).plusDays(ReturnPolicy.WINDOW_DAYS);
        if (LocalDate.ofInstant(now, ZONE).isAfter(last)) {
            throw ApiException.badRequest(EReturnError.RET_002, ReturnPolicy.WINDOW_DAYS);
        }
    }

    /**
     * @param taken units of each order line already returned or in an open return
     * @param earned points the order earned when delivered
     */
    public static Quote quote(CustomerOrder o, List<CustomerOrderLine> orderLines, Map<Long, Integer> taken,
                              List<Requested> requested, EReturnReason reason, BigDecimal pickupFee, int earned) {
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        requested.forEach(r -> wanted.merge(r.itemId(), r.quantity(), Integer::sum));
        BigDecimal subtotal = o.getSubtotal();
        BigDecimal net = subtotal.subtract(nz(o.getDiscount())).subtract(BigDecimal.valueOf(o.getPointsUsed()));
        List<LineQuote> lines = new ArrayList<>();
        BigDecimal goods = BigDecimal.ZERO;
        BigDecimal share = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            CustomerOrderLine line = orderLines.stream().filter(l -> l.getItemId().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest(EReturnError.RET_003, e.getKey(), 0));
            int left = line.getQuantity() - taken.getOrDefault(line.getId(), 0);
            if (e.getValue() < 1 || e.getValue() > left) {
                throw ApiException.badRequest(EReturnError.RET_003, e.getKey(), Math.max(left, 0));
            }
            BigDecimal g = line.getUnitPrice().multiply(BigDecimal.valueOf(e.getValue()));
            BigDecimal s = subtotal.signum() == 0 ? BigDecimal.ZERO
                    : g.multiply(net).divide(subtotal, 0, RoundingMode.HALF_UP);
            lines.add(new LineQuote(line, e.getValue(), g, s));
            goods = goods.add(g);
            share = share.add(s);
            cost = cost.add(nz(line.getUnitCost()).multiply(BigDecimal.valueOf(e.getValue())));
        }
        BigDecimal fee = reason.chargesFee() ? nz(pickupFee) : BigDecimal.ZERO;
        BigDecimal refund = share.subtract(fee);
        if (refund.signum() <= 0) {
            throw ApiException.badRequest(EReturnError.RET_004, fee.stripTrailingZeros().toPlainString());
        }
        return new Quote(lines, goods, share, fee, refund, cost,
                proRata(o.getPointsUsed(), goods, subtotal), proRata(earned, goods, subtotal));
    }

    /** Refund after a switch of reason at approval: same lines and share, new fee. */
    public static BigDecimal refund(BigDecimal share, EReturnReason reason, BigDecimal pickupFee) {
        BigDecimal fee = reason.chargesFee() ? nz(pickupFee) : BigDecimal.ZERO;
        BigDecimal refund = share.subtract(fee);
        if (refund.signum() <= 0) {
            throw ApiException.badRequest(EReturnError.RET_004, fee.stripTrailingZeros().toPlainString());
        }
        return refund;
    }

    /** goods = share net of VAT, service = fee net of VAT, tax = share VAT − fee VAT (never negative: fee < share). */
    public static JournalAmounts journal(BigDecimal share, BigDecimal fee, BigDecimal cost) {
        BigDecimal shareVat = vat(share);
        BigDecimal feeVat = vat(fee);
        return new JournalAmounts(share.subtract(shareVat), fee.subtract(feeVat), shareVat.subtract(feeVat), cost);
    }

    static BigDecimal vat(BigDecimal grossInclVat) {
        return grossInclVat.divide(BigDecimal.valueOf(11), 0, RoundingMode.HALF_UP);
    }

    static int proRata(int points, BigDecimal goods, BigDecimal subtotal) {
        if (points <= 0 || subtotal.signum() == 0) {
            return 0;
        }
        return BigDecimal.valueOf(points).multiply(goods).divide(subtotal, 0, RoundingMode.FLOOR).intValue();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
