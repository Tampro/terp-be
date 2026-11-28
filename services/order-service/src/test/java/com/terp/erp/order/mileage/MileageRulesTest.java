package com.terp.erp.order.mileage;

import com.terp.erp.order.common.EMileageError;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MileageRulesTest {
    static MileageLot lot(long id, int remaining, String expires) {
        MileageLot l = new MileageLot();
        l.setId(id);
        l.setPoints(remaining);
        l.setRemaining(remaining);
        l.setExpiresAt(Instant.parse(expires));
        return l;
    }

    @Test
    void earnIsRoundedDown() {
        assertThat(MileageRules.earn(new BigDecimal("18999"), new BigDecimal("1.5"))).isEqualTo(284);
        assertThat(MileageRules.earn(new BigDecimal("50"), BigDecimal.ONE)).isZero();
        assertThat(MileageRules.earn(new BigDecimal("1000"), BigDecimal.ZERO)).isZero();
    }

    @Test
    void spendRefusals() {
        MileagePolicy p = MileagePolicy.defaults(10L);
        BigDecimal goods = new BigDecimal("8000");
        assertThat(MileageRules.checkSpend(p, 1000, 5000, goods)).isNull();
        assertThat(MileageRules.checkSpend(p, 990, 5000, goods).error()).isEqualTo(EMileageError.MIL_004);
        assertThat(MileageRules.checkSpend(p, 1005, 5000, goods).error()).isEqualTo(EMileageError.MIL_004);
        assertThat(MileageRules.checkSpend(p, 6000, 5000, goods).error()).isEqualTo(EMileageError.MIL_005);
        assertThat(MileageRules.checkSpend(p, 9000, 20000, goods).error()).isEqualTo(EMileageError.MIL_006);
        p.setActive(false);
        assertThat(MileageRules.checkSpend(p, 1000, 5000, goods).error()).isEqualTo(EMileageError.MIL_003);
    }

    @Test
    void spendingTakesTheSoonestExpiringLotsFirst() {
        List<MileageRules.Take> t = MileageRules.split(List.of(lot(1, 300, "2027-01-01T00:00:00Z"),
                lot(2, 1000, "2027-06-01T00:00:00Z")), 800);
        assertThat(t).extracting(x -> x.lot().getId()).containsExactly(1L, 2L);
        assertThat(t).extracting(MileageRules.Take::points).containsExactly(300, 500);
    }

    @Test
    void expiryAddsCalendarMonthsInKorea() {
        // 2026-01-31 10:00 KST + 1 month = 2026-02-28 10:00 KST
        assertThat(MileageRules.expiresAt(Instant.parse("2026-01-31T01:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-02-28T01:00:00Z"));
    }
}
