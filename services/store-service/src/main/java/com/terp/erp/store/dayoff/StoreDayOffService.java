package com.terp.erp.store.dayoff;

import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.common.Texts;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Day-offs of a store: single local dates, today or later, one per date. */
@Service
@RequiredArgsConstructor
public class StoreDayOffService {
    private final StoreDayOffRepository dayOffs;
    private final StoreService stores;
    private final Clock clock;

    public record DayOffForm(
            @Schema(description = "yyyy-MM-dd in the store's time zone; today or later", example = "2026-12-25",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(example = "Christmas") @Size(max = 255) String reason) {
    }

    public record DayOffDto(String id, String storeId, LocalDate date, String reason, Instant createdDate) {
    }

    /** from defaults to today in the store zone; to (inclusive) is optional. */
    @Transactional(readOnly = true)
    public List<DayOffDto> list(Long storeId, LocalDate from, LocalDate to) {
        Store s = stores.requireStore(storeId);
        LocalDate start = from == null ? today(s) : from;
        List<StoreDayOff> rows = to == null
                ? dayOffs.findByStoreIdAndOffDateGreaterThanEqualOrderByOffDate(s.getId(), start)
                : dayOffs.findByStoreIdAndOffDateBetweenOrderByOffDate(s.getId(), start, to);
        return rows.stream().map(StoreDayOffService::dto).toList();
    }

    @Transactional
    public DayOffDto create(Long storeId, DayOffForm f) {
        Store s = stores.requireStore(storeId);
        if (f.date().isBefore(today(s))) {
            throw ApiException.badRequest(EStoreError.STO_012);
        }
        if (dayOffs.existsByStoreIdAndOffDate(s.getId(), f.date())) {
            throw ApiException.badRequest(EStoreError.STO_011);
        }
        StoreDayOff d = new StoreDayOff();
        d.setCompanyId(s.getCompanyId());
        d.setStoreId(s.getId());
        d.setOffDate(f.date());
        d.setReason(Texts.blankToNull(f.reason()));
        try {
            return dto(dayOffs.saveAndFlush(d));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest(EStoreError.STO_011); // a concurrent add of the same date hit uk_store_day_off
        }
    }

    @Transactional
    public void delete(Long storeId, Long dayOffId) {
        Store s = stores.requireStore(storeId);
        dayOffs.delete(dayOffs.findByIdAndStoreId(dayOffId, s.getId())
                .orElseThrow(() -> ApiException.notFound(EStoreError.STO_013)));
    }

    private LocalDate today(Store s) {
        return LocalDate.now(clock.withZone(ZoneId.of(s.getTimeZone())));
    }

    private static DayOffDto dto(StoreDayOff d) {
        return new DayOffDto(Texts.id(d.getId()), Texts.id(d.getStoreId()), d.getOffDate(), d.getReason(),
                d.getCreatedDate());
    }
}
