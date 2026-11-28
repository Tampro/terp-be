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

    public record ShopLookupDto(String companyId, String slug) {
    }

    @GetMapping("/{slug}")
    public ApiMessageDto<ShopLookupDto> lookup(@PathVariable String slug) {
        long companyId = shops.company(slug);
        return ApiMessageDto.ok(new ShopLookupDto(String.valueOf(companyId), slug.trim().toLowerCase(java.util.Locale.ROOT)));
    }
}
