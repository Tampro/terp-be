package com.terp.erp.inventory.transfer;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.inventory.transfer.StockTransferService.TransferDto;
import com.terp.erp.inventory.transfer.StockTransferService.TransferForm;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.terp.erp.inventory.common.Texts.COMPANY;

@Tag(name = "Stock transfers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/transfers")
@SecurityRequirement(name = "bearerAuth")
public class StockTransferController {
    private static final String NOT_FOUND = "No such transfer in your company (INV_023)";

    private final StockTransferService service;

    @Operation(summary = "Stock transfers of the company",
            description = "Filters: from/to warehouse, item, transfer date range (inclusive).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TransferDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long fromWarehouseId,
            @RequestParam(required = false) Long toWarehouseId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @ParameterObject @PageableDefault(size = 20, sort = "transferDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, fromWarehouseId, toWarehouseId, itemId,
                dateFrom, dateTo, pageable)));
    }

    @Operation(summary = "Stock transfer details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TransferDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Transfer stock between two warehouses",
            description = "Moves the quantity out of the source and into the destination in one transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001), same "
                    + "warehouses (INV_022), inactive warehouse (INV_015), service item (INV_016), "
                    + "not enough stock in the source (INV_017)"),
            @ApiResponse(responseCode = "404", description = "Warehouse (INV_007) or item (INV_009) not in the company")})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping
    public ApiMessageDto<TransferDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody TransferForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Delete a stock transfer",
            description = "Soft delete that moves the quantity back from the destination to the source.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The destination no longer holds the quantity (INV_017)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
