package com.terp.erp.inventory.catalog;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.inventory.catalog.CatalogService.CategoryDto;
import com.terp.erp.inventory.catalog.CatalogService.CategoryForm;
import com.terp.erp.inventory.catalog.CatalogService.TaxDto;
import com.terp.erp.inventory.catalog.CatalogService.TaxForm;
import com.terp.erp.inventory.catalog.CatalogService.UnitDto;
import com.terp.erp.inventory.catalog.CatalogService.UnitForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

import static com.terp.erp.inventory.common.Texts.COMPANY;

@Tag(name = "Inventory catalogue (categories, taxes, units)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory")
@SecurityRequirement(name = "bearerAuth")
public class CatalogController {
    private static final String NO_COMPANY = "Super admin without companyId (INV_001)";
    private static final String IN_USE = "Still used by an item (INV_006)";

    private final CatalogService service;

    // ---- categories

    @Operation(summary = "Categories of the company", description = "All, by name (not paged).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','POS_SELL')")
    @GetMapping("/categories")
    public ApiMessageDto<List<CategoryDto>> categories(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.categories(companyId));
    }

    @Operation(summary = "Add a category")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (INV_005), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping("/categories")
    public ApiMessageDto<CategoryDto> createCategory(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.createCategory(companyId, form));
    }

    @Operation(summary = "Edit a category", description = "Full replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (INV_005)"),
            @ApiResponse(responseCode = "404", description = "No such category in your company (INV_002)")})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/categories/{id}")
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.updateCategory(id, form));
    }

    @Operation(summary = "Delete a category", description = "Soft delete; refused while an item uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = IN_USE),
            @ApiResponse(responseCode = "404", description = "No such category in your company (INV_002)")})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/categories/{id}")
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiMessageDto.ok(null);
    }

    // ---- taxes

    @Operation(summary = "Taxes of the company", description = "All, by name (not paged).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','POS_SELL')")
    @GetMapping("/taxes")
    public ApiMessageDto<List<TaxDto>> taxes(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.taxes(companyId));
    }

    @Operation(summary = "Add a tax")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping("/taxes")
    public ApiMessageDto<TaxDto> createTax(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody TaxForm form) {
        return ApiMessageDto.ok(service.createTax(companyId, form));
    }

    @Operation(summary = "Edit a tax", description = "Full replacement: a missing rate becomes 0.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such tax in your company (INV_003)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/taxes/{id}")
    public ApiMessageDto<TaxDto> updateTax(@PathVariable Long id, @Valid @RequestBody TaxForm form) {
        return ApiMessageDto.ok(service.updateTax(id, form));
    }

    @Operation(summary = "Delete a tax", description = "Soft delete; refused while an item uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = IN_USE),
            @ApiResponse(responseCode = "404", description = "No such tax in your company (INV_003)")})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/taxes/{id}")
    public ApiMessageDto<Void> deleteTax(@PathVariable Long id) {
        service.deleteTax(id);
        return ApiMessageDto.ok(null);
    }

    // ---- units

    @Operation(summary = "Units of the company", description = "All, by name (not paged).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping("/units")
    public ApiMessageDto<List<UnitDto>> units(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.units(companyId));
    }

    @Operation(summary = "Add a unit")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping("/units")
    public ApiMessageDto<UnitDto> createUnit(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody UnitForm form) {
        return ApiMessageDto.ok(service.createUnit(companyId, form));
    }

    @Operation(summary = "Edit a unit", description = "Full replacement.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such unit in your company (INV_004)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/units/{id}")
    public ApiMessageDto<UnitDto> updateUnit(@PathVariable Long id, @Valid @RequestBody UnitForm form) {
        return ApiMessageDto.ok(service.updateUnit(id, form));
    }

    @Operation(summary = "Delete a unit", description = "Soft delete; refused while an item uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = IN_USE),
            @ApiResponse(responseCode = "404", description = "No such unit in your company (INV_004)")})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/units/{id}")
    public ApiMessageDto<Void> deleteUnit(@PathVariable Long id) {
        service.deleteUnit(id);
        return ApiMessageDto.ok(null);
    }
}
