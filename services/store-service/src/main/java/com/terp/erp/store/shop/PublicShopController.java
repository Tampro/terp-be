package com.terp.erp.store.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.store.store.StoreService.PublicResolveDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** No token: reads only, rate limited per IP at the gateway (PublicShopRateLimitFilter). */
@Tag(name = "Public storefront (no token)")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/shop/{slug}")
public class PublicShopController {
    private static final String SHOP_404 = "Unknown slug, shop disabled or company not active (STO_016)";
    private static final String UNAVAILABLE = "Inventory (STO_006) or IAM / service account (STO_018) unavailable";

    private final PublicShopService service;

    @Operation(summary = "Which store delivers to this address?", description = "Nearest store of the shop whose "
            + "active zone contains the point. A store closed by the hour or on a day-off is still returned, with "
            + "orderableNow=false and opensAt, so its catalogue can be browsed.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "lat/lon missing or out of range (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = SHOP_404 + ", no store delivers here (STO_010)"),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @GetMapping("/resolve")
    public ApiMessageDto<PublicResolveDto> resolve(
            @PathVariable String slug,
            @Parameter(example = "37.4800") @RequestParam @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @Parameter(example = "126.8900") @RequestParam @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lon) {
        return ApiMessageDto.ok(service.resolve(slug, lat, lon));
    }

    @Operation(summary = "Categories of the shop", description = "[{id, name, color}] ordered by name.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = SHOP_404),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @GetMapping("/categories")
    public ApiMessageDto<JsonNode> categories(@PathVariable String slug) {
        return ApiMessageDto.ok(service.categories(slug));
    }

    @Operation(summary = "Items on sale at a store", description = "Page of {itemId, name, sku, categoryId, salePrice "
            + "(this store's), purchaseLimitQty, soldOut, mainImageUrl}; only ON_SALE store items of live items. "
            + "mainImageUrl is a signed link (no token) valid until the page's imagesExpireAt (epoch ms); null when no image or "
            + "media is unavailable.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "page/size/keyword out of range (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = SHOP_404 + ", store not of the shop or inactive (STO_001)"),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @GetMapping("/stores/{storeId}/items")
    public ApiMessageDto<JsonNode> items(@PathVariable String slug, @PathVariable long storeId,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) @Size(max = 100) String keyword,
                                         @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiMessageDto.ok(service.items(slug, storeId, categoryId, keyword, page, size));
    }

    @Operation(summary = "Item details at a store", description = "{item (with mainImageUrl), shortDescription, description, "
            + "imageUrls (main image first, then the others), imagesExpireAt}; signed links, refetch after imagesExpireAt.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = SHOP_404 + ", store not of the shop or inactive "
            + "(STO_001), item not on sale at this store (STO_017)"),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @GetMapping("/stores/{storeId}/items/{itemId}")
    public ApiMessageDto<JsonNode> item(@PathVariable String slug, @PathVariable long storeId, @PathVariable long itemId) {
        return ApiMessageDto.ok(service.item(slug, storeId, itemId));
    }
}
