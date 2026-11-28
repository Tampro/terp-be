package com.terp.erp.order.mileage;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EMileageError;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The points of a customer in a company: lots spent FIFO under a row lock in the order's own transaction, given back
 * exactly on cancel / expiry, earned once per delivered order, expired per lot. The balance is always derived.
 */
@Component
@RequiredArgsConstructor
public class MileageLedger {
    private final MileagePolicyRepository policies;
    private final MileageLotRepository lots;
    private final MileageEntryRepository entries;
    private final MileageUseLotRepository useLots;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MileagePolicy policy(long companyId) {
        return policies.findById(companyId).orElseGet(() -> MileagePolicy.defaults(companyId));
    }

    @Transactional(readOnly = true)
    public long balance(long companyId, long customerId) {
        return lots.balance(companyId, customerId, clock.instant());
    }

    /** For the cart: null when the points can be used, else the refusal (never locks, never fails). */
    @Transactional(readOnly = true)
    public MileageRules.Refusal preview(long companyId, long customerId, int points, BigDecimal goodsLeft) {
        return MileageRules.checkSpend(policy(companyId), points, balance(companyId, customerId), goodsLeft);
    }

    /** Checks the spend under the lots' lock (400 MIL_003..MIL_006); call {@link #reserve} once the order exists. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MileageLot> lockAndCheck(long companyId, long customerId, int points, BigDecimal goodsLeft) {
        List<MileageLot> open = lots.lockOpen(companyId, customerId, clock.instant());
        long balance = open.stream().mapToLong(MileageLot::getRemaining).sum();
        MileageRules.Refusal r = MileageRules.checkSpend(policy(companyId), points, balance, goodsLeft);
        if (r != null) {
            throw r.arg() == null ? ApiException.badRequest(r.error()) : ApiException.badRequest(r.error(), r.arg());
        }
        return open;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<MileageLot> locked, CustomerOrder o, int points) {
        for (MileageRules.Take t : MileageRules.split(locked, points)) {
            t.lot().setRemaining(t.lot().getRemaining() - t.points());
            lots.save(t.lot());
            MileageUseLot u = new MileageUseLot();
            u.setOrderId(o.getId());
            u.setLotId(t.lot().getId());
            u.setPoints(t.points());
            useLots.save(u);
        }
        entry(o.getCompanyId(), o.getCustomerId(), EMileageEntryType.USE, -points, o, null, null);
    }

    /** Gives back exactly the lots an order's use took; nothing when it used none. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(CustomerOrder o) {
        List<MileageUseLot> taken = useLots.findByOrderId(o.getId());
        if (taken.isEmpty()) {
            return;
        }
        int total = 0;
        for (MileageUseLot u : taken) {
            lots.lockById(u.getLotId()).ifPresent(l -> {
                l.setRemaining(l.getRemaining() + u.getPoints());
                lots.save(l);
            });
            total += u.getPoints();
        }
        useLots.deleteAll(taken);
        entry(o.getCompanyId(), o.getCustomerId(), EMileageEntryType.RELEASE, total, o, null, null);
    }

    /** Once per order: earnRate % of the goods after coupon and points; inactive policy or 0 points → nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int earn(CustomerOrder o, Instant deliveredAt) {
        MileagePolicy p = policy(o.getCompanyId());
        if (!p.isActive() || lots.existsByEarnOrderId(o.getId())) {
            return 0;
        }
        BigDecimal goods = o.getSubtotal().subtract(nz(o.getDiscount())).subtract(BigDecimal.valueOf(o.getPointsUsed()));
        int points = MileageRules.earn(goods, p.getEarnRate());
        if (points <= 0) {
            return 0;
        }
        MileageLot l = lot(o.getCompanyId(), o.getCustomerId(), "EARN", points,
                MileageRules.expiresAt(deliveredAt, p.getExpiryMonths()));
        l.setEarnOrderId(o.getId());
        l = lots.save(l);
        entry(o.getCompanyId(), o.getCustomerId(), EMileageEntryType.EARN, points, o, l.getId(), null);
        return points;
    }

    /** Back Office: + adds a lot with the policy's expiry; − is spent FIFO like a use (400 MIL_005 over the balance). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjust(long companyId, long customerId, int points, String reason) {
        Instant now = clock.instant();
        if (points > 0) {
            MileageLot l = lots.save(lot(companyId, customerId, "ADJUST", points,
                    MileageRules.expiresAt(now, policy(companyId).getExpiryMonths())));
            entry(companyId, customerId, EMileageEntryType.ADJUST, points, null, l.getId(), reason);
            return;
        }
        List<MileageLot> open = lots.lockOpen(companyId, customerId, now);
        long balance = open.stream().mapToLong(MileageLot::getRemaining).sum();
        if (-points > balance) {
            throw ApiException.badRequest(EMileageError.MIL_005, balance);
        }
        for (MileageRules.Take t : MileageRules.split(open, -points)) {
            t.lot().setRemaining(t.lot().getRemaining() - t.points());
            lots.save(t.lot());
        }
        entry(companyId, customerId, EMileageEntryType.ADJUST, points, null, null, reason);
    }

    /** One overdue lot: what is left of it expires. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int expire(long lotId) {
        MileageLot l = lots.lockById(lotId).orElse(null);
        if (l == null || l.getRemaining() <= 0 || l.getExpiresAt().isAfter(clock.instant())) {
            return 0;
        }
        int left = l.getRemaining();
        l.setRemaining(0);
        lots.save(l);
        entry(l.getCompanyId(), l.getCustomerId(), EMileageEntryType.EXPIRE, -left, null, l.getId(), null);
        return left;
    }

    private MileageLot lot(long companyId, long customerId, String source, int points, Instant expiresAt) {
        MileageLot l = new MileageLot();
        l.setCompanyId(companyId);
        l.setCustomerId(customerId);
        l.setSource(source);
        l.setPoints(points);
        l.setRemaining(points);
        l.setExpiresAt(expiresAt);
        l.setCreatedDate(clock.instant());
        return l;
    }

    private void entry(long companyId, long customerId, EMileageEntryType type, int points, CustomerOrder o, Long lotId,
                       String reason) {
        MileageEntry e = new MileageEntry();
        e.setCompanyId(companyId);
        e.setCustomerId(customerId);
        e.setType(type);
        e.setPoints(points);
        if (o != null) {
            e.setOrderId(o.getId());
            e.setOrderNo(o.getOrderNo());
        }
        e.setLotId(lotId);
        e.setReason(reason);
        e.setCreatedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse("system"));
        e.setCreatedDate(clock.instant());
        entries.save(e);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
