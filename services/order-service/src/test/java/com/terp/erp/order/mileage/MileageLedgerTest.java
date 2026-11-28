package com.terp.erp.order.mileage;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EMileageError;
import com.terp.erp.order.order.CustomerOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MileageLedgerTest {
    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");
    private final MileagePolicyRepository policies = mock(MileagePolicyRepository.class);
    private final MileageLotRepository lots = mock(MileageLotRepository.class);
    private final MileageEntryRepository entries = mock(MileageEntryRepository.class);
    private final MileageUseLotRepository useLots = mock(MileageUseLotRepository.class);
    private final MileageLedger ledger = new MileageLedger(policies, lots, entries, useLots, Clock.fixed(NOW, ZoneOffset.UTC));
    private final CustomerOrder order = new CustomerOrder();
    private final List<MileageUseLot> savedUses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        order.setId(100L);
        order.setCompanyId(10L);
        order.setCustomerId(5L);
        order.setOrderNo("HE-20261128-000001");
        order.setSubtotal(new BigDecimal("20000"));
        order.setDiscount(new BigDecimal("1500"));
        when(policies.findById(10L)).thenReturn(Optional.empty());
        when(lots.save(any())).thenAnswer(a -> {
            MileageLot l = a.getArgument(0);
            if (l.getId() == null) {
                l.setId(77L);
            }
            return l;
        });
        when(useLots.save(any())).thenAnswer(a -> {
            savedUses.add(a.getArgument(0));
            return a.getArgument(0);
        });
    }

    @Test
    void reserveTakesFifoAndReleaseGivesTheSameLotsBack() {
        MileageLot a = MileageRulesTest.lot(1, 300, "2027-01-01T00:00:00Z");
        MileageLot b = MileageRulesTest.lot(2, 1000, "2027-06-01T00:00:00Z");
        when(lots.lockOpen(10L, 5L, NOW)).thenReturn(List.of(a, b));
        List<MileageLot> locked = ledger.lockAndCheck(10L, 5L, 1000, new BigDecimal("18500"));
        ledger.reserve(locked, order, 1000);
        assertThat(a.getRemaining()).isZero();
        assertThat(b.getRemaining()).isEqualTo(300);
        assertThat(savedUses).extracting(MileageUseLot::getPoints).containsExactly(300, 700);

        when(useLots.findByOrderId(100L)).thenReturn(savedUses);
        when(lots.lockById(1L)).thenReturn(Optional.of(a));
        when(lots.lockById(2L)).thenReturn(Optional.of(b));
        ledger.release(order);
        assertThat(a.getRemaining()).isEqualTo(300);
        assertThat(b.getRemaining()).isEqualTo(1000);
        ArgumentCaptor<MileageEntry> e = ArgumentCaptor.forClass(MileageEntry.class);
        verify(entries, org.mockito.Mockito.times(2)).save(e.capture());
        assertThat(e.getAllValues()).extracting(MileageEntry::getType, MileageEntry::getPoints)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(EMileageEntryType.USE, -1000),
                        org.assertj.core.groups.Tuple.tuple(EMileageEntryType.RELEASE, 1000));
    }

    @Test
    void overTheBalanceIsRefusedUnderTheLock() {
        when(lots.lockOpen(10L, 5L, NOW)).thenReturn(List.of(MileageRulesTest.lot(1, 500, "2027-01-01T00:00:00Z")));
        assertThatThrownBy(() -> ledger.lockAndCheck(10L, 5L, 1000, new BigDecimal("18500")))
                .satisfies(x -> assertThat(((ApiException) x).getErrorCode()).isEqualTo(EMileageError.MIL_005));
    }

    @Test
    void anOrderEarnsOnceOnTheGoodsAfterCouponAndPoints() {
        order.setPointsUsed(1000);                                              // 20,000 − 1,500 − 1,000 = 17,500 → 1 %
        assertThat(ledger.earn(order, NOW)).isEqualTo(175);
        ArgumentCaptor<MileageLot> l = ArgumentCaptor.forClass(MileageLot.class);
        verify(lots).save(l.capture());
        assertThat(l.getValue().getEarnOrderId()).isEqualTo(100L);
        assertThat(l.getValue().getExpiresAt()).isEqualTo(Instant.parse("2027-11-28T03:00:00Z"));
        when(lots.existsByEarnOrderId(100L)).thenReturn(true);
        assertThat(ledger.earn(order, NOW)).isZero();
    }

    @Test
    void anInactivePolicyEarnsNothing() {
        MileagePolicy off = MileagePolicy.defaults(10L);
        off.setActive(false);
        when(policies.findById(10L)).thenReturn(Optional.of(off));
        assertThat(ledger.earn(order, NOW)).isZero();
        verify(lots, never()).save(any());
    }

    @Test
    void anOverdueLotExpiresWhatIsLeft() {
        MileageLot old = MileageRulesTest.lot(3, 400, "2026-11-01T00:00:00Z");
        old.setCompanyId(10L);
        old.setCustomerId(5L);
        when(lots.lockById(3L)).thenReturn(Optional.of(old));
        assertThat(ledger.expire(3L)).isEqualTo(400);
        assertThat(old.getRemaining()).isZero();
        assertThat(ledger.expire(3L)).isZero();
    }
}
