package com.terp.erp.store.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.client.StorefrontClient;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import com.terp.erp.store.store.StoreService.PublicResolveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The public storefront (no token). The company is taken from the shop slug only: unknown slug, disabled shop or a
 * company that is not active (IAM) → 404 STO_016; a store id of another company or an inactive store → 404 STO_001.
 * Catalogue data is read from inventory as store-service's service account.
 */
@Service
@RequiredArgsConstructor
public class PublicShopService {
    private final ShopSettingRepository settings;
    private final StoreService stores;
    private final StorefrontClient storefront;

    public PublicResolveDto resolve(String slug, BigDecimal lat, BigDecimal lon) {
        return stores.resolveIn(company(slug), lat, lon);
    }

    public JsonNode categories(String slug) {
        return storefront.categories(company(slug));
    }

    public JsonNode items(String slug, long storeId, Long categoryId, String keyword, int page, int size) {
        long companyId = company(slug);
        Store s = stores.requireActiveIn(companyId, storeId);
        return storefront.shelf(companyId, s.getWarehouseId(), categoryId, keyword, page, size);
    }

    public JsonNode item(String slug, long storeId, long itemId) {
        long companyId = company(slug);
        Store s = stores.requireActiveIn(companyId, storeId);
        return storefront.item(companyId, s.getWarehouseId(), itemId);
    }

    long company(String slug) {
        ShopSetting shop = settings.findBySlug(slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT))
                .filter(ShopSetting::isEnabled)
                .orElseThrow(() -> ApiException.notFound(EStoreError.STO_016));
        if (!storefront.companyActive(shop.getCompanyId())) {
            throw ApiException.notFound(EStoreError.STO_016);
        }
        return shop.getCompanyId();
    }
}
