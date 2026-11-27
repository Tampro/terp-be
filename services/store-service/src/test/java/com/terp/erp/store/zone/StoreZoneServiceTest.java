package com.terp.erp.store.zone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import com.terp.erp.store.zone.StoreZoneService.GeoJsonPolygon;
import com.terp.erp.store.zone.StoreZoneService.ZoneForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreZoneServiceTest {
    private final StoreZoneRepository zones = mock(StoreZoneRepository.class);
    private final StoreService stores = mock(StoreService.class);
    private final StoreZoneService service = new StoreZoneService(zones, stores, new ObjectMapper());

    @BeforeEach
    void setUp() {
        Store s = new Store();
        s.setId(7L);
        s.setCompanyId(10L);
        when(stores.requireStore(7L)).thenReturn(s);
        when(stores.requireStore(8L)).thenThrow(ApiException.notFound(EStoreError.STO_001));
        when(zones.isValid(anyString())).thenReturn(1);
        when(zones.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void openRingIsClosedAndCompanyCopied() {
        StoreZoneService.ZoneDto d = service.create(7L, form(ring(4, false)));
        List<List<BigDecimal>> r = d.area().coordinates().get(0);
        assertThat(r).hasSize(5);
        assertThat(r.get(4)).isEqualTo(r.get(0));
        assertThat(d.storeId()).isEqualTo("7");
        assertThat(d.active()).isTrue();
    }

    @Test
    void badShapesAreRefusedBeforeTheDatabase() {
        List<List<BigDecimal>> square = ring(4, true);
        assertCode(new GeoJsonPolygon("MultiPolygon", List.of(square)));
        assertCode(new GeoJsonPolygon("Polygon", List.of(square, square)));       // a hole
        assertCode(new GeoJsonPolygon("Polygon", List.of(ring(2, false))));       // 2 vertices
        assertCode(new GeoJsonPolygon("Polygon", List.of(ring(501, false))));     // over 500
        List<List<BigDecimal>> lat91 = new ArrayList<>(square);
        lat91.set(1, List.of(new BigDecimal("126.9"), new BigDecimal("91")));
        assertCode(new GeoJsonPolygon("Polygon", List.of(lat91)));
        verify(zones, never()).isValid(anyString());
    }

    @Test
    void invalidForMysqlIsRefused() {
        when(zones.isValid(anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.create(7L, form(ring(4, true))))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_009));
        verify(zones, never()).saveAndFlush(any());
    }

    @Test
    void zoneOfAnotherStoreAndStoreOfAnotherCompany() {
        when(zones.findByIdAndStoreId(3L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(7L, 3L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_008));
        assertThatThrownBy(() -> service.list(8L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_001));
    }

    @Test
    void deleteIsHard() {
        StoreZone z = new StoreZone();
        when(zones.findByIdAndStoreId(3L, 7L)).thenReturn(Optional.of(z));
        service.delete(7L, 3L);
        verify(zones).delete(z);
    }

    private void assertCode(GeoJsonPolygon p) {
        assertThatThrownBy(() -> service.create(7L, form(p)))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_009));
    }

    private static ZoneForm form(List<List<BigDecimal>> ring) {
        return form(new GeoJsonPolygon("Polygon", List.of(ring)));
    }

    private static ZoneForm form(GeoJsonPolygon p) {
        return new ZoneForm("Zone", p, null);
    }

    /** n distinct points on a small circle around Seoul, optionally closed. */
    private static List<List<BigDecimal>> ring(int n, boolean closed) {
        List<List<BigDecimal>> r = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            r.add(List.of(BigDecimal.valueOf(126.9 + 0.01 * Math.cos(a)), BigDecimal.valueOf(37.5 + 0.01 * Math.sin(a))));
        }
        if (closed) {
            r.add(r.get(0));
        }
        return r;
    }
}
