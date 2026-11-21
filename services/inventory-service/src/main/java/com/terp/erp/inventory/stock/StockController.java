package com.terp.erp.inventory.stock;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.inventory.stock.StockService.AddStockForm;
import com.terp.erp.inventory.stock.StockService.MovesForm;
import com.terp.erp.inventory.stock.StockService.MovesResult;
import com.terp.erp.inventory.stock.StockService.StockDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.terp.erp.inventory.common.Texts.COMPANY;

@Tag(name = "Stock")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/stock")
@SecurityRequirement(name = "bearerAuth")
public class StockController {
    private static final String LINE_ERRORS = "item not of the company (INV_020), service item (INV_016), warehouse "
            + "not of the company or inactive (INV_015), not enough stock — names SKU and available (INV_017)";

    private final StockService service;

    @Operation(summary = "Stock per item and warehouse", description = "Both filters optional.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_VIEW','SALES_VIEW','POS_SELL')")
    @GetMapping
    public ApiMessageDto<List<StockDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long warehouseId) {
        return ApiMessageDto.ok(service.find(companyId, itemId, warehouseId));
    }

    @Operation(summary = "Add stock", description = "Adds quantity (> 0) to an item in a warehouse and writes a "
            + "MANUAL movement. Answers the new quantity of that warehouse.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = LINE_ERRORS + ", super admin without "
            + "companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.has('INVENTORY_MANAGE')")
    @PostMapping("/add")
    public ApiMessageDto<StockDto> add(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                       @Valid @RequestBody AddStockForm form) {
        return ApiMessageDto.ok(service.add(companyId, form));
    }

    @Operation(summary = "Apply a document's stock moves",
            description = "All-or-nothing, rows locked. Idempotent per (company, sourceType, sourceId): when movements "
                    + "of that source already exist nothing changes and applied = false.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = LINE_ERRORS + "; nothing applied. Super admin "
            + "without companyId (INV_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('INVENTORY_MANAGE','SALES_POST','POS_SELL')")
    @PostMapping("/moves")
    public ApiMessageDto<MovesResult> moves(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody MovesForm form) {
        return ApiMessageDto.ok(service.moves(companyId, form));
    }
}
