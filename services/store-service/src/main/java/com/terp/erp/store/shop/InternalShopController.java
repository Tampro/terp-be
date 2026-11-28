package com.terp.erp.store.shop;

import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (SecurityConfig internalChain, erp.store.internal.client-ids): the company of an open shop.
 * Unknown slug, disabled shop and inactive company all answer 404 STO_016, the same rule the public storefront applies.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/store/shops")
public class InternalShopController {
    private final PublicShopService shops;
    private final com.terp.erp.store.store.StoreService stores;
    private final com.terp.erp.store.zone.StoreZoneRepository zones;

    public record ShopLookupDto(String companyId, String slug) {
    }

    /** What a cart needs of a store of this shop: its warehouse, fees and minimums, and whether it takes orders now. */
    public record StoreViewDto(String id, String name, String warehouseId, java.math.BigDecimal deliveryFee,
                               java.math.BigDecimal freeDeliveryThreshold, java.math.BigDecimal minOrderAmount,
                               java.math.BigDecimal firstOrderMinAmount, java.math.BigDecimal bagFee, boolean orderableNow,
                               java.time.Instant opensAt) {
    }

    /** Store of the shop's company, live and active, else 404 (STO_016 shop, STO_001 store). */
    @GetMapping("/{slug}/stores/{storeId}")
    public ApiMessageDto<StoreViewDto> store(@PathVariable String slug, @PathVariable long storeId) {
        com.terp.erp.store.store.Store s = stores.requireActiveIn(shops.company(slug), storeId);
        var o = stores.orderableNow(s);
        return ApiMessageDto.ok(new StoreViewDto(String.valueOf(s.getId()), s.getName(), String.valueOf(s.getWarehouseId()),
                s.getDeliveryFee(), s.getFreeDeliveryThreshold(), s.getMinOrderAmount(), s.getFirstOrderMinAmount(),
                s.getBagFee(), o.orderableNow(), o.opensAt()));
    }

    /** Whether this store of the shop delivers to (lat, lon): an active zone of the store contains the point. */
    @GetMapping("/{slug}/stores/{storeId}/serves")
    public ApiMessageDto<java.util.Map<String, Boolean>> serves(@PathVariable String slug, @PathVariable long storeId,
                                                                @org.springframework.web.bind.annotation.RequestParam java.math.BigDecimal lat,
                                                                @org.springframework.web.bind.annotation.RequestParam java.math.BigDecimal lon) {
        com.terp.erp.store.store.Store s = stores.requireActiveIn(shops.company(slug), storeId);
        return ApiMessageDto.ok(java.util.Map.of("serves", zones.countServing(s.getId(), lat, lon) > 0));
    }

    @GetMapping("/{slug}")
    public ApiMessageDto<ShopLookupDto> lookup(@PathVariable String slug) {
        long companyId = shops.company(slug);
        return ApiMessageDto.ok(new ShopLookupDto(String.valueOf(companyId), slug.trim().toLowerCase(java.util.Locale.ROOT)));
    }
}
