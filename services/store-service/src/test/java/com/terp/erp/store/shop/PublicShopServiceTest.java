package com.terp.erp.store.shop;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void listReplacesImageIdsBySignedUrls() throws Exception {
        Store s = new Store();
        s.setWarehouseId(245L);
        when(stores.requireActiveIn(10L, 7L)).thenReturn(s);
        when(storefront.shelf(10L, 245L, null, null, 0, 20)).thenReturn(json(
                "{\"content\":[{\"itemId\":\"1\",\"mainImageId\":\"31\"},{\"itemId\":\"2\",\"mainImageId\":null}],\"page\":0}"));
        when(storefront.imageLinks(10L, java.util.Set.of(31L)))
                .thenReturn(new StorefrontClient.ImageLinks(java.util.Map.of(31L, "http://g/api/v1/media/files/31/content?sig=x"), 99L));
        JsonNode page = service.items("express", 7L, null, null, 0, 20);
        assertThat(page.at("/content/0/mainImageUrl").asText()).contains("/files/31/content");
        assertThat(page.at("/content/1/mainImageUrl").isNull()).isTrue();
        assertThat(page.at("/content/0").has("mainImageId")).isFalse();
        assertThat(page.get("imagesExpireAt").asLong()).isEqualTo(99L);
    }

    @Test
    void detailPutsTheMainImageFirstAndDropsIds() throws Exception {
        Store s = new Store();
        s.setWarehouseId(245L);
        when(stores.requireActiveIn(10L, 7L)).thenReturn(s);
        when(storefront.item(10L, 245L, 1L)).thenReturn(json(
                "{\"item\":{\"itemId\":\"1\",\"mainImageId\":\"31\"},\"imageIds\":[\"32\",\"31\",\"33\"]}"));
        when(storefront.imageLinks(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StorefrontClient.ImageLinks(java.util.Map.of(31L, "u31", 32L, "u32"), 5L));
        JsonNode d = service.item("express", 7L, 1L);
        assertThat(d.get("imageUrls")).extracting(JsonNode::asText).containsExactly("u31", "u32");
        assertThat(d.has("imageIds")).isFalse();
        assertThat(d.at("/item/mainImageUrl").asText()).isEqualTo("u31");
        assertThat(d.at("/item").has("mainImageId")).isFalse();
    }

    @Test
    void noLinksGivesNullsAndAnEmptyList() throws Exception {
        Store s = new Store();
        s.setWarehouseId(245L);
        when(stores.requireActiveIn(10L, 7L)).thenReturn(s);
        when(storefront.item(10L, 245L, 1L)).thenReturn(json("{\"item\":{\"itemId\":\"1\",\"mainImageId\":\"31\"},\"imageIds\":[]}"));
        when(storefront.imageLinks(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StorefrontClient.ImageLinks(java.util.Map.of(), null));
        JsonNode d = service.item("express", 7L, 1L);
        assertThat(d.get("imageUrls")).isEmpty();
        assertThat(d.at("/item/mainImageUrl").isNull()).isTrue();
        assertThat(d.get("imagesExpireAt").isNull()).isTrue();
    }

    private static JsonNode json(String s) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
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
