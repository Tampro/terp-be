package com.terp.erp.store.store;

import com.terp.erp.exception.ApiException;
import com.terp.erp.store.TestLogin;
import com.terp.erp.store.client.InventoryClient;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.store.StoreService.OrderableDto;
import com.terp.erp.store.store.StoreService.StoreForm;
import com.terp.erp.store.zone.StoreZoneRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final InventoryClient inventory = mock(InventoryClient.class);
    private final StoreZoneRepository zones = mock(StoreZoneRepository.class);
    private final StoreService service = new StoreService(stores, inventory, zones,
            Clock.fixed(Instant.parse("2026-11-27T03:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "STORE_MANAGE");
        when(stores.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
        when(stores.findFirstByCompanyIdAndWarehouseIdAndDeletedFalse(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(inventory.activeWarehouse(anyLong())).thenReturn(new InventoryClient.Warehouse("245", "W", true));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsWithDefaults() {
        StoreService.StoreDto d = service.create(null, form("0293", 245L, "10:00", "22:00"));
        assertThat(d.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(d.deliveryFee()).isEqualByComparingTo("0");
        assertThat(d.active()).isTrue();
        verify(inventory).activeWarehouse(245L);
    }

    @Test
    void duplicateCodeIsRefused() {
        when(stores.existsByCompanyIdAndCodeAndDeletedFalseAndIdNot(10L, "0293", -1L)).thenReturn(true);
        assertCode(() -> service.create(null, form("0293", 245L, "10:00", "22:00")), EStoreError.STO_002);
    }

    @Test
    void warehouseOfAnotherStoreIsRefused() {
        Store other = store(5L, 10L);
        other.setName("Express Gasan");
        when(stores.findFirstByCompanyIdAndWarehouseIdAndDeletedFalse(10L, 245L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.create(null, form("0610", 245L, "10:00", "22:00"))).satisfies(e -> {
            ApiException a = (ApiException) e;
            assertThat(a.getErrorCode()).isEqualTo(EStoreError.STO_003);
            assertThat(a.getArgs()).containsExactly("Express Gasan");
        });
    }

    @Test
    void inactiveOrForeignWarehouseIsRefused() {
        doThrow(ApiException.badRequest(EStoreError.STO_004)).when(inventory).activeWarehouse(99L);
        assertCode(() -> service.create(null, form("0293", 99L, "10:00", "22:00")), EStoreError.STO_004);
        verify(stores, never()).saveAndFlush(any());
    }

    @Test
    void inventoryDownIsReportedNotHidden() {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EStoreError.STO_006)).when(inventory).activeWarehouse(245L);
        assertCode(() -> service.create(null, form("0293", 245L, "10:00", "22:00")), EStoreError.STO_006);
        verify(stores, never()).saveAndFlush(any());
    }

    @Test
    void hoursMustStartBeforeTheyEndAndZoneMustExist() {
        assertCode(() -> service.create(null, form("0293", 245L, "10:00", "09:00")), EStoreError.STO_005);
        assertCode(() -> service.create(null, form("0293", 245L, "10:00", "10:00")), EStoreError.STO_005);
        StoreForm badZone = new StoreForm("0293", "S", 245L, null, null, null, null, "Mars/Base", LocalTime.of(10, 0),
                LocalTime.of(22, 0), null, null, null, null, null, null);
        assertCode(() -> service.create(null, badZone), EStoreError.STO_005);
    }

    @Test
    void anotherCompanysStoreAnswersNotFound() {
        Store s = store(7L, 20L);
        when(stores.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(s));
        assertCode(() -> service.get(7L), EStoreError.STO_001);
        assertCode(() -> service.delete(7L), EStoreError.STO_001);
        assertThat(s.isDeleted()).isFalse();
    }

    @Test
    void deleteFreesCodeAndWarehouse() {
        Store s = store(7L, 10L);
        when(stores.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(s));
        service.delete(7L);
        assertThat(s.isDeleted()).isTrue();
        assertThat(s.getDeletedKey()).isEqualTo(7L);
    }

    @Test
    void updateWithTheSameWarehouseDoesNotCallInventoryAgain() {
        Store s = store(7L, 10L);
        when(stores.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(s));
        when(stores.findFirstByCompanyIdAndWarehouseIdAndDeletedFalse(10L, 245L)).thenReturn(Optional.of(s));
        service.update(7L, form("0293", 245L, "08:00", "23:00"));
        assertThat(s.getOrderStart()).isEqualTo(LocalTime.of(8, 0));
        verify(inventory, never()).activeWarehouse(eq(245L));
    }

    @Test
    void orderableAtTheWindowBoundariesInTheStoreZone() {
        Store s = store(7L, 10L);
        when(stores.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(s));
        // 21:59:59 KST = 12:59:59Z → open
        assertThat(service.orderable(7L, Instant.parse("2026-11-27T12:59:59Z"))).isEqualTo(new OrderableDto(true, null));
        // 22:00:00 KST → closed, opens 10:00 KST next day = 01:00Z
        assertThat(service.orderable(7L, Instant.parse("2026-11-27T13:00:00Z")))
                .isEqualTo(new OrderableDto(false, Instant.parse("2026-11-28T01:00:00Z")));
        // 09:59 KST → closed, opens 10:00 KST the same day
        assertThat(service.orderable(7L, Instant.parse("2026-11-27T00:59:00Z")))
                .isEqualTo(new OrderableDto(false, Instant.parse("2026-11-27T01:00:00Z")));
        // no instant → the fixed clock (03:00Z = 12:00 KST) → open
        assertThat(service.orderable(7L, null).orderableNow()).isTrue();
        s.setActive(false);
        assertThat(service.orderable(7L, Instant.parse("2026-11-27T03:00:00Z"))).isEqualTo(new OrderableDto(false, null));
    }

    @Test
    void resolveWithoutZoneAnswersStoNotFound() {
        when(zones.resolve(eq(10L), any(), any())).thenReturn(Optional.empty());
        assertCode(() -> service.resolve(null, new BigDecimal("37.48"), new BigDecimal("126.89")), EStoreError.STO_010);
    }

    @Test
    void resolveReturnsTheStoreEvenWhenClosed() {
        Store s = store(7L, 10L);
        s.setOrderStart(LocalTime.of(20, 0));
        s.setOrderEnd(LocalTime.of(22, 0));
        when(stores.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(s));
        StoreZoneRepository.Hit hit = mock(StoreZoneRepository.Hit.class);
        when(hit.getStoreId()).thenReturn(7L);
        when(hit.getZoneId()).thenReturn(3L);
        when(zones.resolve(eq(10L), any(), any())).thenReturn(Optional.of(hit));
        // fixed clock 03:00Z = 12:00 KST → closed, opens 20:00 KST = 11:00Z
        StoreService.ResolveDto r = service.resolve(null, new BigDecimal("37.48"), new BigDecimal("126.89"));
        assertThat(r.store().id()).isEqualTo("7");
        assertThat(r.zoneId()).isEqualTo("3");
        assertThat(r.orderableNow()).isFalse();
        assertThat(r.opensAt()).isEqualTo(Instant.parse("2026-11-27T11:00:00Z"));
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EStoreError code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }

    private static StoreForm form(String code, Long warehouseId, String start, String end) {
        return new StoreForm(code, "Express " + code, warehouseId, null, null, null, null, null, LocalTime.parse(start),
                LocalTime.parse(end), null, null, null, null, null, null);
    }

    private static Store store(Long id, Long companyId) {
        Store s = new Store();
        s.setId(id);
        s.setCompanyId(companyId);
        s.setCode("0293");
        s.setName("Express 0293");
        s.setWarehouseId(245L);
        s.setTimeZone("Asia/Seoul");
        s.setOrderStart(LocalTime.of(10, 0));
        s.setOrderEnd(LocalTime.of(22, 0));
        s.setActive(true);
        return s;
    }
}
