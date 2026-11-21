package com.terp.erp.inventory.item;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.inventory.item.Item.EItemType;
import com.terp.erp.inventory.item.ItemService.ItemCreateForm;
import com.terp.erp.inventory.item.ItemService.ItemDto;
import com.terp.erp.inventory.item.ItemService.ItemUpdateForm;
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
import org.springframework.data.domain.Sort;
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

@Tag(name = "Items (products, services, parts)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/items")
@SecurityRequirement(name = "bearerAuth")
public class ItemController {
    private static final String NOT_FOUND = "No such item in your company (INV_009)";
    private static final String FORM_ERRORS = "SKU already used (INV_010), category/unit/tax not of the company "
            + "(INV_012, INV_013, INV_014)";

    private final ItemService service;

    @Operation(summary = "Items of the company", description = "keyword = name or SKU; filters type, categoryId. "
            + "quantity = stock summed over warehouses (0 for SERVICE).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ItemDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EItemType type,
            @RequestParam(required = false) Long categoryId,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, type, categoryId, pageable)));
    }

    @Operation(summary = "Item details", description = "Also read by commerce-service (sales, quotations).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','SALES_VIEW','QUOTATION_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ItemDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an item", description = "sku is required unless generateSku = true. "
            + "openingWarehouseId + openingQuantity (PRODUCT/PART only) write the opening stock and an OPENING movement.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = FORM_ERRORS + ", SKU missing (INV_011), "
            + "opening stock on a service (INV_016), opening warehouse/quantity incomplete (INV_018), opening "
            + "warehouse not of the company or inactive (INV_015), SKU generation failed (INV_021), super admin "
            + "without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping
    public ApiMessageDto<ItemDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody ItemCreateForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an item", description = "Full replacement. Stock is not changed here (use /stock).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", a stocked item cannot become a "
                    + "service (INV_019)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ItemDto> update(@PathVariable Long id, @Valid @RequestBody ItemUpdateForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an item", description = "Soft delete.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
