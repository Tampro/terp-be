package com.terp.erp.commerce.purchase;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.commerce.purchase.PurchaseReturnService.ReturnDetail;
import com.terp.erp.commerce.purchase.PurchaseReturnService.ReturnForm;
import com.terp.erp.commerce.purchase.PurchaseReturnService.ReturnRow;
import com.terp.erp.commerce.purchase.PurchaseReturnService.ReturnableLine;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Purchase — returns")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/purchase/returns")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseReturnController {
    private static final String WRITE_ERRORS = "Invoice not posted (PUR_006), more than returnable (PUR_007), line "
            + "not of the invoice (PUR_011), not a draft (PUR_009), validation (COMMON_001)";
    private final PurchaseReturnService service;

    @Operation(summary = "Purchase returns", description = "Number PR-YYYY-MM-NNN.")
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ReturnRow>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long invoiceId, @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) PurchaseReturn.EStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Matches the number or the vendor") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, invoiceId, vendorId, warehouseId, status,
                from, to, keyword, pageable)));
    }

    @Operation(summary = "Return with its lines")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such return in your company (PUR_008)"))
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ReturnDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Lines of a posted purchase invoice with the quantity still returnable")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Invoice not posted (PUR_006), nothing left to "
            + "return (PUR_012)"),
            @ApiResponse(responseCode = "404", description = "No such invoice (PUR_001)")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_VIEW')")
    @GetMapping("/returnable")
    public ApiMessageDto<List<ReturnableLine>> returnable(@RequestParam Long invoiceId) {
        return ApiMessageDto.ok(service.returnable(invoiceId));
    }

    @Operation(summary = "Create a purchase return (DRAFT)")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = "PUR_001")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ReturnDetail> create(@Valid @RequestBody ReturnForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a draft return", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = "PUR_008")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ReturnDetail> update(@PathVariable Long id, @Valid @RequestBody ReturnForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a draft return")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (PUR_009)"),
            @ApiResponse(responseCode = "404", description = "PUR_008")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Approve: DRAFT → APPROVED")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (PUR_009)"),
            @ApiResponse(responseCode = "404", description = "PUR_008")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_POST')")
    @PostMapping("/{id}/approve")
    public ApiMessageDto<ReturnDetail> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(service.approve(id));
    }

    @Operation(summary = "Complete: APPROVED → COMPLETED", description = "Once the goods are sent back; stocked lines "
            + "leave the sending warehouse.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not approved (PUR_010), not enough stock "
            + "(INV_ codes relayed)"),
            @ApiResponse(responseCode = "404", description = "PUR_008"),
            @ApiResponse(responseCode = "503", description = "Inventory unavailable (SAL_015)")})
    @PreAuthorize("@PermissionChecker.has('PURCHASE_POST')")
    @PostMapping("/{id}/complete")
    public ApiMessageDto<ReturnDetail> complete(@PathVariable Long id) {
        return ApiMessageDto.ok(service.complete(id));
    }
}
