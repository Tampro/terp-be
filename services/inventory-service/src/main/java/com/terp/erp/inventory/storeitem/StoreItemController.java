package com.terp.erp.inventory.storeitem;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.inventory.storeitem.StoreItemService.BulkForm;
import com.terp.erp.inventory.storeitem.StoreItemService.BulkResult;
import com.terp.erp.inventory.storeitem.StoreItemService.StatusForm;
import com.terp.erp.inventory.storeitem.StoreItemService.StoreItemDto;
import com.terp.erp.inventory.storeitem.StoreItemService.StoreItemForm;
import com.terp.erp.inventory.storeitem.StoreItemService.StoreItemUpdateForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.terp.erp.inventory.common.Texts.COMPANY;

@Tag(name = "Store items (price and on-sale status per store)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/store-items")
@SecurityRequirement(name = "bearerAuth")
public class StoreItemController {
    private static final String NOT_FOUND = "No such store item in your company (INV_027)";
    private static final String REFS = "item not of the company (INV_020), service item (INV_016), warehouse not of "
            + "the company or inactive (INV_015)";

    private final StoreItemService service;

    @Operation(summary = "Store items of the company", description = "A store is a warehouse; an item without a "
            + "live row is not sold there. keyword = item name or SKU. stockQuantity = stock of the item in that "
            + "warehouse (0 when none).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<StoreItemDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) EStoreItemStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, warehouseId, categoryId, status, keyword, pageable)));
    }

    @Operation(summary = "Store item details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<StoreItemDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Sell an item at a store", description = "status defaults to ON_SALE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = REFS + ", already set up for this store "
            + "(INV_026), super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping
    public ApiMessageDto<StoreItemDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody StoreItemForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Change price, status and limit", description = "Full replacement of salePrice, status "
            + "and purchaseLimitQty (omitted purchaseLimitQty = no limit); warehouse and item never change.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<StoreItemDto> update(@PathVariable Long id, @Valid @RequestBody StoreItemUpdateForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Put on sale or suspend")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<StoreItemDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.status(id, form));
    }

    @Operation(summary = "Stop selling an item at a store", description = "Soft delete; the pair can be set up again.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Upsert many store items", description = "By (warehouseId, sku), at most 1000 lines, all or "
            + "nothing: one bad line writes nothing. Sending the same body again updates the same rows.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad line (INV_028: line number + reason code "
            + "INV_020 unknown SKU, INV_016 service item, INV_015 warehouse, INV_026 pair repeated), more than 1000 "
            + "lines (INV_029), concurrent duplicate (INV_026), super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping("/bulk")
    public ApiMessageDto<BulkResult> bulk(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody BulkForm form) {
        return ApiMessageDto.ok(service.bulk(companyId, form));
    }
}
