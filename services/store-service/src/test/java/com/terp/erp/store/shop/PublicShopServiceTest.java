package com.terp.erp.store.shop;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.store.client.StorefrontClient;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicShopServiceTest {
    private final ShopSettingRepository settings = mock(ShopSettingRepository.class);
    private final StoreService stores = mock(StoreService.class);
    private final StorefrontClient storefront = mock(StorefrontClient.class);
    private final PublicShopService service = new PublicShopService(settings, stores, storefront);

    @BeforeEach
    void setUp() {
        when(settings.findBySlug("express")).thenReturn(Optional.of(shop(10L, true)));
        when(settings.findBySlug("closed")).thenReturn(Optional.of(shop(11L, false)));
        when(settings.findBySlug("suspended")).thenReturn(Optional.of(shop(12L, true)));
        when(storefront.companyActive(10L)).thenReturn(true);
        when(storefront.companyActive(12L)).thenReturn(false);
    }

    @Test
    void slugIsNormalisedAndResolvesTheCompany() {
        assertThat(service.company("  Express ")).isEqualTo(10L);
    }

    @Test
    void unknownDisabledOrInactiveCompanyLooksMissing() {
        assertCode(() -> service.company("nope"), EStoreError.STO_016);
        assertCode(() -> service.company("closed"), EStoreError.STO_016);
        assertCode(() -> service.company("suspended"), EStoreError.STO_016);
        assertCode(() -> service.company(null), EStoreError.STO_016);
        verify(storefront, never()).companyActive(11L);
    }

    @Test
    void itemsReadTheStoresOwnWarehouseOfTheShopsCompany() {
        Store s = new Store();
        s.setWarehouseId(245L);
        when(stores.requireActiveIn(10L, 7L)).thenReturn(s);
        service.items("express", 7L, null, "egg", 0, 20);
        verify(storefront).shelf(10L, 245L, null, "egg", 0, 20);
    }

    @Test
    void aStoreOfAnotherCompanyIsNotReachedThroughThisShop() {
        when(stores.requireActiveIn(10L, 99L)).thenThrow(ApiException.notFound(EStoreError.STO_001));
        assertCode(() -> service.item("express", 99L, 1L), EStoreError.STO_001);
        verify(storefront, never()).item(anyLong(), anyLong(), anyLong());
    }

    private static ShopSetting shop(Long companyId, boolean enabled) {
        ShopSetting s = new ShopSetting();
        s.setCompanyId(companyId);
        s.setEnabled(enabled);
        return s;
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
