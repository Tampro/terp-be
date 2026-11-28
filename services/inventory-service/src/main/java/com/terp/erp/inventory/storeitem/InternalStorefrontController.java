package com.terp.erp.inventory.storeitem;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.inventory.storeitem.StorefrontReadService.CategoryDto;
import com.terp.erp.inventory.storeitem.StorefrontReadService.ShelfItemDetailDto;
import com.terp.erp.inventory.storeitem.StorefrontReadService.ShelfItemDto;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only catalogue for store-service's public storefront. Service-account tokens only (SecurityConfig internalChain,
 * azp allow-list); not routed by the gateway, so hidden from the public Swagger.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/inventory")
public class InternalStorefrontController {
    private final StorefrontReadService service;
    private final com.terp.erp.inventory.stock.StockService stock;

    @GetMapping("/categories")
    public ApiMessageDto<List<CategoryDto>> categories(@RequestParam long companyId) {
        return ApiMessageDto.ok(service.categories(companyId));
    }

    @GetMapping("/warehouses/{warehouseId}/shelf")
    public ApiMessageDto<ResponseListDto<ShelfItemDto>> shelf(
            @PathVariable long warehouseId, @RequestParam long companyId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.shelf(companyId, warehouseId, categoryId, keyword, pageable)));
    }

    /**
     * A paid order's stock out of the store's warehouse (order-service): all-or-nothing, idempotent on
     * (company, sourceType, sourceId) — a retry answers applied=false. Not enough stock → 400 INV_017.
     */
    @org.springframework.web.bind.annotation.PostMapping("/stock/moves")
    public ApiMessageDto<com.terp.erp.inventory.stock.StockService.MovesResult> moves(
            @RequestParam long companyId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.terp.erp.inventory.stock.StockService.MovesForm form) {
        return ApiMessageDto.ok(stock.movesFor(companyId, form));
    }

    @GetMapping("/warehouses/{warehouseId}/shelf/items")
    public ApiMessageDto<List<ShelfItemDto>> shelfItems(@PathVariable long warehouseId, @RequestParam long companyId,
                                                        @RequestParam(required = false) List<Long> ids) {
        return ApiMessageDto.ok(service.shelfItems(companyId, warehouseId, ids));
    }

    @GetMapping("/warehouses/{warehouseId}/shelf/{itemId}")
    public ApiMessageDto<ShelfItemDetailDto> detail(@PathVariable long warehouseId, @PathVariable long itemId,
                                                    @RequestParam long companyId) {
        return ApiMessageDto.ok(service.detail(companyId, warehouseId, itemId));
    }
}
