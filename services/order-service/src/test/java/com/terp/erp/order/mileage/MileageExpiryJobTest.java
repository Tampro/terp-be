package com.terp.erp.order.mileage;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MileageExpiryJobTest {
    @Test
    void eachOverdueLotExpiresInItsOwnTransaction() {
        Instant now = Instant.parse("2026-11-28T18:10:00Z");
        MileageLotRepository lots = mock(MileageLotRepository.class);
        MileageLedger ledger = mock(MileageLedger.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(lots.overdue(now)).thenReturn(List.of(1L, 2L));
        when(ledger.expire(1L)).thenReturn(300);
        when(ledger.expire(2L)).thenReturn(0);
        assertThat(new MileageExpiryJob(lots, ledger, tx, Clock.fixed(now, ZoneOffset.UTC)).run()).isEqualTo(300);
    }
}
