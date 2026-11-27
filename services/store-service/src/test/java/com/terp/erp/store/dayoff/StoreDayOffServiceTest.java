package com.terp.erp.store.dayoff;

import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.dayoff.StoreDayOffService.DayOffForm;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreDayOffServiceTest {
    private final StoreDayOffRepository dayOffs = mock(StoreDayOffRepository.class);
    private final StoreService stores = mock(StoreService.class);
    // 2026-11-27T16:00Z = 01:00 KST on the 28th: "today" is the 28th in the store zone, the 27th in UTC
    private final StoreDayOffService service = new StoreDayOffService(dayOffs, stores,
            Clock.fixed(Instant.parse("2026-11-27T16:00:00Z"), ZoneOffset.UTC));
    private final LocalDate today = LocalDate.of(2026, 11, 28);

    @BeforeEach
    void setUp() {
        Store s = new Store();
        s.setId(7L);
        s.setCompanyId(10L);
        s.setTimeZone("Asia/Seoul");
        when(stores.requireStore(7L)).thenReturn(s);
        when(stores.requireStore(8L)).thenThrow(ApiException.notFound(EStoreError.STO_001));
        when(dayOffs.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void todayInTheStoreZoneIsAllowedYesterdayIsNot() {
        StoreDayOffService.DayOffDto d = service.create(7L, new DayOffForm(today, "  "));
        assertThat(d.date()).isEqualTo(today);
        assertThat(d.reason()).isNull();
        assertCode(() -> service.create(7L, new DayOffForm(today.minusDays(1), null)), EStoreError.STO_012);
    }

    @Test
    void duplicateDateIsRefusedAlsoOnARace() {
        when(dayOffs.existsByStoreIdAndOffDate(7L, today)).thenReturn(true);
        assertCode(() -> service.create(7L, new DayOffForm(today, null)), EStoreError.STO_011);
        verify(dayOffs, never()).saveAndFlush(any());
        when(dayOffs.existsByStoreIdAndOffDate(7L, today.plusDays(1))).thenReturn(false);
        when(dayOffs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_store_day_off"));
        assertCode(() -> service.create(7L, new DayOffForm(today.plusDays(1), null)), EStoreError.STO_011);
    }

    @Test
    void dayOffOfAnotherStoreAndStoreOfAnotherCompany() {
        when(dayOffs.findByIdAndStoreId(3L, 7L)).thenReturn(Optional.empty());
        assertCode(() -> service.delete(7L, 3L), EStoreError.STO_013);
        assertCode(() -> service.list(8L, null, null), EStoreError.STO_001);
    }

    @Test
    void listDefaultsFromTodayInTheStoreZone() {
        service.list(7L, null, null);
        verify(dayOffs).findByStoreIdAndOffDateGreaterThanEqualOrderByOffDate(7L, today);
        service.list(7L, today, today.plusDays(5));
        verify(dayOffs).findByStoreIdAndOffDateBetweenOrderByOffDate(7L, today, today.plusDays(5));
    }

    @Test
    void deleteIsHard() {
        StoreDayOff d = new StoreDayOff();
        when(dayOffs.findByIdAndStoreId(3L, 7L)).thenReturn(Optional.of(d));
        service.delete(7L, 3L);
        verify(dayOffs).delete(d);
    }

    private static void assertCode(ThrowingCallable call, EStoreError code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
