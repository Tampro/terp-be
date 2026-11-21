package com.terp.erp.inventory.warehouse;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.inventory.warehouse.WarehouseService.WarehouseDto;
import com.terp.erp.inventory.warehouse.WarehouseService.WarehouseForm;
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

@Tag(name = "Warehouses")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/warehouses")
@SecurityRequirement(name = "bearerAuth")
public class WarehouseController {
    private static final String NOT_FOUND = "No such warehouse in your company (INV_007)";

    private final WarehouseService service;

    @Operation(summary = "Warehouses of the company", description = "keyword = name or city (contains); city = exact city, case-insensitive; "
            + "active = status (true active, false inactive).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','POS_SELL')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<WarehouseDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, city, active, pageable)));
    }

    @Operation(summary = "Warehouse details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','POS_SELL')")
    @GetMapping("/{id}")
    public ApiMessageDto<WarehouseDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a warehouse")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping
    public ApiMessageDto<WarehouseDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody WarehouseForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a warehouse", description = "Full replacement: a missing active becomes true.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<WarehouseDto> update(@PathVariable Long id, @Valid @RequestBody WarehouseForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a warehouse", description = "Soft delete; refused while it holds stock > 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still holds stock (INV_008)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
