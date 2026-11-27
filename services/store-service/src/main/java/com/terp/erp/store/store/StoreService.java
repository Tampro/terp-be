package com.terp.erp.store.store;

import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.store.client.InventoryClient;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.dayoff.StoreDayOffRepository;
import com.terp.erp.store.common.Texts;
import com.terp.erp.store.zone.StoreZoneRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stores of a chain: the warehouse each sells from, one daily order window in its own time zone, delivery fees. */
@Service
@RequiredArgsConstructor
public class StoreService {
    // TODO(assumption): the chain's stores are in Korea (Homeplus Express); a store of another country names its zone.
    public static final String DEFAULT_ZONE = "Asia/Seoul";
    /** How far opensAt looks past day-offs (user decision 2026-11-28). */
    static final int SEARCH_DAYS = 366;

    private final StoreRepository stores;
    private final InventoryClient inventory;
    private final StoreZoneRepository zones;
    private final StoreDayOffRepository dayOffs;
    private final Clock clock;

    public record StoreForm(
            @Schema(description = "Unique in the company", example = "0293", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @Schema(example = "Express Gasan", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(description = "Active warehouse of the company, not linked to another store", example = "245",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId,
            @Schema(example = "1 Example-ro, Seoul") @Size(max = 500) String address,
            @Schema(example = "37.4752") @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @Schema(example = "126.8940") @DecimalMin("-180") @DecimalMax("180") BigDecimal lon,
            @Schema(example = "02-000-0000") @Size(max = 30) String phone,
            @Schema(description = "IANA zone, default Asia/Seoul", example = "Asia/Seoul") @Size(max = 40) String timeZone,
            @Schema(description = "HH:mm, before orderEnd (same day)", example = "10:00",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime orderStart,
            @Schema(description = "HH:mm, exclusive", example = "22:00", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalTime orderEnd,
            @Schema(description = "Default 0", example = "3000") @DecimalMin("0") BigDecimal deliveryFee,
            @Schema(description = "Free delivery from this amount; default 0", example = "30000") @DecimalMin("0")
            BigDecimal freeDeliveryThreshold,
            @Schema(description = "Minimum after discounts; default 0", example = "20000") @DecimalMin("0")
            BigDecimal minOrderAmount,
            @Schema(description = "Minimum of a customer's first order; default 0", example = "10000") @DecimalMin("0")
            BigDecimal firstOrderMinAmount,
            @Schema(description = "Paid bag; default 0", example = "200") @DecimalMin("0") BigDecimal bagFee,
            @Schema(description = "Default true") Boolean active) {
    }

    public record StoreDto(String id, String code, String name, String warehouseId, String address, BigDecimal lat,
                           BigDecimal lon, String phone, String timeZone, LocalTime orderStart, LocalTime orderEnd,
                           BigDecimal deliveryFee, BigDecimal freeDeliveryThreshold, BigDecimal minOrderAmount,
                           BigDecimal firstOrderMinAmount, BigDecimal bagFee, boolean active, Instant createdDate,
                           Instant updatedDate) {
    }

    /** The store delivering to a point, with whether it takes an order now. */
    public record ResolveDto(StoreDto store, String zoneId, boolean orderableNow, Instant opensAt) {
    }

    /** opensAt: the next start of the window (UTC) after the hour and day-offs; null when open, inactive or no opening within 366 days. */
    public record OrderableDto(boolean orderableNow, Instant opensAt) {
    }

    @Transactional(readOnly = true)
    public Page<StoreDto> search(Long requestedCompanyId, String keyword, Boolean active, Pageable pageable) {
        return stores.search(Texts.company(requestedCompanyId), Texts.kw(keyword), active, pageable).map(StoreService::dto);
    }

    @Transactional(readOnly = true)
    public StoreDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public StoreDto create(Long requestedCompanyId, StoreForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        Store s = new Store();
        s.setCompanyId(companyId);
        apply(s, f);
        return dto(save(s));
    }

    /** Full replacement of every field; a changed warehouse is checked again. */
    @Transactional
    public StoreDto update(Long id, StoreForm f) {
        Store s = require(id);
        apply(s, f);
        return dto(save(s));
    }

    @Transactional
    public void delete(Long id) {
        Store s = require(id);
        s.setDeleted(true);
        s.setDeletedKey(s.getId());
    }

    /** Not a day-off and [orderStart, orderEnd) in the store's zone. */
    @Transactional(readOnly = true)
    public OrderableDto orderable(Long id, Instant at) {
        return orderable(require(id), at == null ? Instant.now(clock) : at);
    }

    private OrderableDto orderable(Store s, Instant at) {
        if (!s.isActive()) {
            return new OrderableDto(false, null);
        }
        ZoneId zone = ZoneId.of(s.getTimeZone());
        ZonedDateTime local = at.atZone(zone);
        LocalDate today = local.toLocalDate();
        // One query for the whole search (NFR-1): the day-offs from today up to the cap.
        Set<LocalDate> offs = new HashSet<>(dayOffs.findOffDates(s.getId(), today, today.plusDays(SEARCH_DAYS + 1)));
        Optional<LocalDate> next = nextOpening(today, local.toLocalTime(), s.getOrderStart(), s.getOrderEnd(), offs);
        if (next.isPresent() && next.get().equals(today) && !local.toLocalTime().isBefore(s.getOrderStart())) {
            return new OrderableDto(true, null);
        }
        return new OrderableDto(false, next.map(d -> d.atTime(s.getOrderStart()).atZone(zone).toInstant()).orElse(null));
    }

    /**
     * The date whose window is open now (today, when inside the window) or opens next, skipping day-offs, at most
     * SEARCH_DAYS ahead; empty when every date up to the cap is a day-off.
     */
    static Optional<LocalDate> nextOpening(LocalDate today, LocalTime now, LocalTime start, LocalTime end,
                                           Set<LocalDate> offs) {
        LocalDate d = offs.contains(today) || !now.isBefore(end) ? today.plusDays(1) : today;
        while (offs.contains(d)) {
            d = d.plusDays(1);
        }
        return d.isAfter(today.plusDays(SEARCH_DAYS)) ? Optional.empty() : Optional.of(d);
    }

    // ------------------------------------------------------------------ helpers

    /** For the zone pages: the live store of the caller's company, else 404 STO_001. */
    public Store requireStore(Long id) {
        return require(id);
    }

    /**
     * The store delivering to (lat, lon): an active zone of a live, active store of the company contains the point;
     * nearest store wins. Returned also when closed by the hour (orderableNow=false), so the catalogue stays readable.
     */
    @Transactional(readOnly = true)
    public ResolveDto resolve(Long requestedCompanyId, BigDecimal lat, BigDecimal lon) {
        Long companyId = Texts.company(requestedCompanyId);
        StoreZoneRepository.Hit hit = zones.resolve(companyId, lat, lon)
                .orElseThrow(() -> ApiException.notFound(EStoreError.STO_010));
        Store s = require(hit.getStoreId());
        OrderableDto o = orderable(s, Instant.now(clock));
        return new ResolveDto(dto(s), Texts.id(hit.getZoneId()), o.orderableNow(), o.opensAt());
    }

    /** By-id access: another company's store answers 404 STO_001. */
    private Store require(Long id) {
        Store s = stores.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EStoreError.STO_001));
        TenantGuard.assertSameCompany(s.getCompanyId(), EStoreError.STO_001);
        return s;
    }

    private void apply(Store s, StoreForm f) {
        String zone = Texts.blankToNull(f.timeZone()) == null ? DEFAULT_ZONE : f.timeZone().trim();
        try {
            ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw ApiException.badRequest(EStoreError.STO_005);
        }
        if (!f.orderStart().isBefore(f.orderEnd())) {
            throw ApiException.badRequest(EStoreError.STO_005);
        }
        String code = f.code().trim();
        Long selfId = s.getId() == null ? -1L : s.getId();
        if (stores.existsByCompanyIdAndCodeAndDeletedFalseAndIdNot(s.getCompanyId(), code, selfId)) {
            throw ApiException.badRequest(EStoreError.STO_002);
        }
        stores.findFirstByCompanyIdAndWarehouseIdAndDeletedFalse(s.getCompanyId(), f.warehouseId())
                .filter(other -> !Objects.equals(other.getId(), s.getId()))
                .ifPresent(other -> {
                    throw ApiException.badRequest(EStoreError.STO_003, other.getName());
                });
        if (!Objects.equals(s.getWarehouseId(), f.warehouseId())) {
            inventory.activeWarehouse(f.warehouseId()); // STO_004 / STO_006, or inventory's own error relayed
        }
        s.setCode(code);
        s.setName(f.name().trim());
        s.setWarehouseId(f.warehouseId());
        s.setAddress(Texts.blankToNull(f.address()));
        s.setLat(f.lat());
        s.setLon(f.lon());
        s.setPhone(Texts.blankToNull(f.phone()));
        s.setTimeZone(zone);
        s.setOrderStart(f.orderStart());
        s.setOrderEnd(f.orderEnd());
        s.setDeliveryFee(orZero(f.deliveryFee()));
        s.setFreeDeliveryThreshold(orZero(f.freeDeliveryThreshold()));
        s.setMinOrderAmount(orZero(f.minOrderAmount()));
        s.setFirstOrderMinAmount(orZero(f.firstOrderMinAmount()));
        s.setBagFee(orZero(f.bagFee()));
        s.setActive(f.active() == null || f.active());
    }

    /** A concurrent writer of the same code / warehouse hits the unique keys: answered as the duplicate, not 500. */
    private Store save(Store s) {
        try {
            return stores.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(e.getMostSpecificCause().getMessage());
            throw ApiException.badRequest(msg.contains("uk_store_warehouse") ? EStoreError.STO_003 : EStoreError.STO_002,
                    s.getName());
        }
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static StoreDto dto(Store s) {
        return new StoreDto(Texts.id(s.getId()), s.getCode(), s.getName(), Texts.id(s.getWarehouseId()), s.getAddress(),
                s.getLat(), s.getLon(), s.getPhone(), s.getTimeZone(), s.getOrderStart(), s.getOrderEnd(),
                s.getDeliveryFee(), s.getFreeDeliveryThreshold(), s.getMinOrderAmount(), s.getFirstOrderMinAmount(),
                s.getBagFee(), s.isActive(), s.getCreatedDate(), s.getUpdatedDate());
    }
}
