package com.terp.erp.commerce.salesreturn;

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
import com.terp.erp.commerce.salesreturn.SalesReturnService.ReturnDetail;
import com.terp.erp.commerce.salesreturn.SalesReturnService.ReturnForm;
import com.terp.erp.commerce.salesreturn.SalesReturnService.ReturnRow;
import com.terp.erp.commerce.salesreturn.SalesReturnService.ReturnableLine;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Sales — returns")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales/returns")
@SecurityRequirement(name = "bearerAuth")
public class SalesReturnController {
    private static final String WRITE_ERRORS = "Invoice not posted (SAL_010), more than returnable (SAL_011), line "
            + "not of the invoice (SAL_018), warehouse on a service invoice (SAL_019), not a draft (SAL_013), "
            + "validation (COMMON_001)";
    private final SalesReturnService service;

    @Operation(summary = "Sales returns", description = "Number SR-YYYY-MM-NNN.")
    @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ReturnRow>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long invoiceId, @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long warehouseId, @RequestParam(required = false) SalesReturn.EStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Matches the number or the customer") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, invoiceId, customerId, warehouseId, status,
                from, to, keyword, pageable)));
    }

    @Operation(summary = "Return with its lines")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such return in your company (SAL_012)"))
    @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ReturnDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Lines of a posted invoice with the quantity still returnable")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Invoice not posted (SAL_010)"),
            @ApiResponse(responseCode = "404", description = "No such invoice (SAL_001)")})
    @PreAuthorize("@PermissionChecker.has('SALES_VIEW')")
    @GetMapping("/returnable")
    public ApiMessageDto<List<ReturnableLine>> returnable(@RequestParam Long invoiceId) {
        return ApiMessageDto.ok(service.returnable(invoiceId));
    }

    @Operation(summary = "Create a return (DRAFT)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = WRITE_ERRORS))
    @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
    @PostMapping
    public ApiMessageDto<ReturnDetail> create(@Valid @RequestBody ReturnForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a draft return", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = "SAL_012")})
    @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ReturnDetail> update(@PathVariable Long id, @Valid @RequestBody ReturnForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a draft return")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (SAL_013)"),
            @ApiResponse(responseCode = "404", description = "SAL_012")})
    @PreAuthorize("@PermissionChecker.has('SALES_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Approve: DRAFT → APPROVED")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a draft (SAL_013)"))
    @PreAuthorize("@PermissionChecker.has('SALES_POST')")
    @PostMapping("/{id}/approve")
    public ApiMessageDto<ReturnDetail> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(service.approve(id));
    }

    @Operation(summary = "Complete: APPROVED → COMPLETED", description = "Once the goods are received; stocked lines "
            + "go back into the receiving warehouse.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not approved (SAL_014)"),
            @ApiResponse(responseCode = "503", description = "Inventory unavailable (SAL_015)")})
    @PreAuthorize("@PermissionChecker.has('SALES_POST')")
    @PostMapping("/{id}/complete")
    public ApiMessageDto<ReturnDetail> complete(@PathVariable Long id) {
        return ApiMessageDto.ok(service.complete(id));
    }

    @Operation(summary = "Re-post the journal of a return whose journal FAILED", description = "Checks the journal "
            + "again with accounting, then requests it again: journalStatus → PENDING. Moves no stock; accounting "
            + "writes the entry once per return.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "journalStatus is not FAILED (SAL_025), closed "
            + "year (ACC_070) / unmapped account (ACC_017) relayed"),
            @ApiResponse(responseCode = "404", description = "No such return in your company (SAL_012)"),
            @ApiResponse(responseCode = "503", description = "Accounting unavailable (SAL_016)")})
    @PreAuthorize("@PermissionChecker.has('SALES_POST')")
    @PostMapping("/{id}/journal/retry")
    public ApiMessageDto<ReturnDetail> retryJournal(@PathVariable Long id) {
        return ApiMessageDto.ok(service.retryJournal(id));
    }
}
