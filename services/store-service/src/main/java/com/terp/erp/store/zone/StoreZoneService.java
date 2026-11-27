package com.terp.erp.store.zone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.common.Texts;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Delivery zones of a store: one GeoJSON Polygon ring each (no holes), shape checked here, validity by MySQL. */
@Service
@RequiredArgsConstructor
public class StoreZoneService {
    static final int MAX_VERTICES = 500;
    private static final BigDecimal LAT = BigDecimal.valueOf(90);
    private static final BigDecimal LON = BigDecimal.valueOf(180);

    private final StoreZoneRepository zones;
    private final StoreService stores;
    private final ObjectMapper mapper;

    @Schema(description = "GeoJSON Polygon with exactly one ring of [lon, lat] positions; an open ring is closed by the server")
    public record GeoJsonPolygon(
            @Schema(example = "Polygon", requiredMode = Schema.RequiredMode.REQUIRED) String type,
            @Schema(example = "[[[126.88,37.47],[126.90,37.47],[126.90,37.49],[126.88,37.49],[126.88,37.47]]]",
                    requiredMode = Schema.RequiredMode.REQUIRED) List<List<List<BigDecimal>>> coordinates) {
    }

    public record ZoneForm(
            @Schema(example = "Gasan 1", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid GeoJsonPolygon area,
            @Schema(description = "Default true") Boolean active) {
    }

    public record ZoneDto(String id, String storeId, String name, GeoJsonPolygon area, boolean active, Instant createdDate,
                          Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public List<ZoneDto> list(Long storeId) {
        Store s = stores.requireStore(storeId);
        return zones.findByStoreIdOrderById(s.getId()).stream().map(this::dto).toList();
    }

    @Transactional
    public ZoneDto create(Long storeId, ZoneForm f) {
        Store s = stores.requireStore(storeId);
        StoreZone z = new StoreZone();
        z.setCompanyId(s.getCompanyId());
        z.setStoreId(s.getId());
        apply(z, f);
        return dto(zones.saveAndFlush(z));
    }

    /** Full replacement of name, area and active. */
    @Transactional
    public ZoneDto update(Long storeId, Long zoneId, ZoneForm f) {
        StoreZone z = require(storeId, zoneId);
        apply(z, f);
        return dto(zones.saveAndFlush(z));
    }

    @Transactional
    public void delete(Long storeId, Long zoneId) {
        zones.delete(require(storeId, zoneId));
    }

    // ------------------------------------------------------------------ helpers

    /** Store first (another company's store answers STO_001), then the zone must belong to it (else STO_008). */
    private StoreZone require(Long storeId, Long zoneId) {
        Store s = stores.requireStore(storeId);
        return zones.findByIdAndStoreId(zoneId, s.getId()).orElseThrow(() -> ApiException.notFound(EStoreError.STO_008));
    }

    private void apply(StoreZone z, ZoneForm f) {
        String json = toJson(closedRing(f.area()));
        Integer valid;
        try {
            valid = zones.isValid(json);
        } catch (DataAccessException e) {
            throw ApiException.badRequest(EStoreError.STO_009);
        }
        if (valid == null || valid != 1) {
            throw ApiException.badRequest(EStoreError.STO_009);
        }
        z.setName(f.name().trim());
        z.setArea(json);
        z.setActive(f.active() == null || f.active());
    }

    /** Shape only (type, one ring, positions, ranges, vertex count); MySQL judges self-intersection afterwards. */
    static GeoJsonPolygon closedRing(GeoJsonPolygon p) {
        if (!"Polygon".equals(p.type()) || p.coordinates() == null || p.coordinates().size() != 1
                || p.coordinates().get(0) == null || p.coordinates().get(0).isEmpty()) {
            throw ApiException.badRequest(EStoreError.STO_009);
        }
        List<List<BigDecimal>> ring = new ArrayList<>(p.coordinates().get(0));
        for (List<BigDecimal> pos : ring) {
            if (pos == null || pos.size() != 2 || pos.get(0) == null || pos.get(1) == null
                    || pos.get(0).abs().compareTo(LON) > 0 || pos.get(1).abs().compareTo(LAT) > 0) {
                throw ApiException.badRequest(EStoreError.STO_009);
            }
        }
        if (!same(ring.get(0), ring.get(ring.size() - 1))) {
            ring.add(ring.get(0));
        }
        HashSet<List<BigDecimal>> distinct = new HashSet<>();
        for (List<BigDecimal> pos : ring) {
            distinct.add(List.of(pos.get(0).stripTrailingZeros(), pos.get(1).stripTrailingZeros()));
        }
        if (distinct.size() < 3 || distinct.size() > MAX_VERTICES) {
            throw ApiException.badRequest(EStoreError.STO_009);
        }
        return new GeoJsonPolygon("Polygon", List.of(ring));
    }

    private static boolean same(List<BigDecimal> a, List<BigDecimal> b) {
        return a.get(0).compareTo(b.get(0)) == 0 && a.get(1).compareTo(b.get(1)) == 0;
    }

    private String toJson(GeoJsonPolygon p) {
        try {
            return mapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest(EStoreError.STO_009);
        }
    }

    private ZoneDto dto(StoreZone z) {
        GeoJsonPolygon area;
        try {
            area = z.getArea() == null ? null : mapper.readValue(z.getArea(), GeoJsonPolygon.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("store_zone " + z.getId() + " area is not GeoJSON", e);
        }
        return new ZoneDto(Texts.id(z.getId()), Texts.id(z.getStoreId()), z.getName(), area, z.isActive(),
                z.getCreatedDate(), z.getUpdatedDate());
    }
}
