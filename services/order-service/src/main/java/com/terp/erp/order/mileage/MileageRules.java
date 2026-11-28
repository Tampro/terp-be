package com.terp.erp.order.mileage;

import com.terp.erp.order.common.EMileageError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** The mileage rules as pure functions (tested apart from any service). 1 point = 1 won. */
public final class MileageRules {
    // TODO(assumption): the chain's stores are in Korea — month arithmetic in the Korean calendar.
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MileageRules() {
    }

    /** Points earned on goods (after coupon and points used), rounded down. */
    public static int earn(BigDecimal goods, BigDecimal ratePercent) {
        if (goods == null || ratePercent == null || goods.signum() <= 0 || ratePercent.signum() <= 0) {
            return 0;
        }
        return goods.multiply(ratePercent).divide(HUNDRED, 0, RoundingMode.FLOOR).intValueExact();
    }

    public record Refusal(EMileageError error, Object arg) {
    }

    /** null when the spend is allowed, else the refusal (MIL_003..MIL_006) with its message argument. */
    public static Refusal checkSpend(MileagePolicy p, int points, long balance, BigDecimal goodsLeft) {
        if (!p.isActive()) {
            return new Refusal(EMileageError.MIL_003, null);
        }
        if (points < p.getMinRedeem() || points % 10 != 0) {
            return new Refusal(EMileageError.MIL_004, p.getMinRedeem());
        }
        if (points > balance) {
            return new Refusal(EMileageError.MIL_005, balance);
        }
        if (BigDecimal.valueOf(points).compareTo(goodsLeft) > 0) {
            return new Refusal(EMileageError.MIL_006, goodsLeft.stripTrailingZeros().toPlainString());
        }
        return null;
    }

    public record Take(MileageLot lot, int points) {
    }

    /** FIFO over lots already sorted by expiry; the caller checked the balance. */
    public static List<Take> split(List<MileageLot> lots, int points) {
        List<Take> out = new ArrayList<>();
        int left = points;
        for (MileageLot l : lots) {
            if (left == 0) {
                break;
            }
            int take = Math.min(left, l.getRemaining());
            if (take > 0) {
                out.add(new Take(l, take));
                left -= take;
            }
        }
        if (left > 0) {
            throw new IllegalStateException("not enough points in the lots");
        }
        return out;
    }

    public static Instant expiresAt(Instant from, int months) {
        return from.atZone(ZONE).plusMonths(months).toInstant();
    }
}
