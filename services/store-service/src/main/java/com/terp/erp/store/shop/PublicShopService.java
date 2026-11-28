package com.terp.erp.store.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.client.StorefrontClient;
import com.terp.erp.store.client.StorefrontClient.ImageLinks;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.store.Store;
import com.terp.erp.store.store.StoreService;
import com.terp.erp.store.store.StoreService.PublicResolveDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
        JsonNode page0 = storefront.shelf(companyId, s.getWarehouseId(), categoryId, keyword, page, size);
        if (!(page0 instanceof ObjectNode pageNode)) {
            return page0;
        }
        List<ObjectNode> rows = new ArrayList<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode n : pageNode.path("content")) {
            if (n instanceof ObjectNode row) {
                rows.add(row);
                id(row.get("mainImageId")).ifPresent(ids::add);
            }
        }
        ImageLinks links = storefront.imageLinks(companyId, ids);
        for (ObjectNode row : rows) {
            String url = id(row.remove("mainImageId")).map(links.urls()::get).orElse(null);
            row.put("mainImageUrl", url);
        }
        putExpiry(pageNode, links);
        return pageNode;
    }

    public JsonNode item(String slug, long storeId, long itemId) {
        long companyId = company(slug);
        Store s = stores.requireActiveIn(companyId, storeId);
        JsonNode detail = storefront.item(companyId, s.getWarehouseId(), itemId);
        if (!(detail instanceof ObjectNode d)) {
            return detail;
        }
        // Main image first, then the additional ones in their order (inventory keeps them apart), each once.
        Set<Long> ids = new LinkedHashSet<>();
        JsonNode item = d.get("item");
        Optional<Long> main = item instanceof ObjectNode o ? id(o.remove("mainImageId")) : Optional.empty();
        main.ifPresent(ids::add);
        JsonNode extra = d.remove("imageIds");
        if (extra != null) {
            extra.forEach(n -> id(n).ifPresent(ids::add));
        }
        ImageLinks links = storefront.imageLinks(companyId, ids);
        ArrayNode urls = d.putArray("imageUrls");
        ids.stream().map(links.urls()::get).filter(java.util.Objects::nonNull).forEach(urls::add);
        if (item instanceof ObjectNode o) {
            o.put("mainImageUrl", main.map(links.urls()::get).orElse(null));
        }
        putExpiry(d, links);
        return d;
    }

    private static void putExpiry(ObjectNode node, ImageLinks links) {
        if (links.expiresAt() == null) {
            node.putNull("imagesExpireAt");
        } else {
            node.put("imagesExpireAt", links.expiresAt());
        }
    }

    /** Ids travel as strings in the inventory JSON; null / blank / not a number → empty. */
    private static Optional<Long> id(JsonNode n) {
        if (n == null || n.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(n.asText().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
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
