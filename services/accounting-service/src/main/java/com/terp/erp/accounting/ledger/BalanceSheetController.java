package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.ledger.BalanceSheetService.CompareDto;
import com.terp.erp.accounting.ledger.BalanceSheetService.GenerateForm;
import com.terp.erp.accounting.ledger.BalanceSheetService.NotesForm;
import com.terp.erp.accounting.ledger.BalanceSheetService.SheetDto;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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
import org.springframework.http.ResponseEntity;
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

@Tag(name = "Balance sheets")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/balance-sheets")
@SecurityRequirement(name = "bearerAuth")
public class BalanceSheetController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String VIEW = "@PermissionChecker.has('LEDGER_REPORT_VIEW')";
    private static final String CLOSE = "@PermissionChecker.has('LEDGER_CLOSE')";
    private static final String NOT_FOUND = "No such balance sheet in your company (ACC_072)";
    private static final String NOT_DRAFT = "Not a draft (ACC_073)";

    private final BalanceSheetService service;

    @Operation(summary = "Generate a balance sheet", description = "DRAFT snapshot of balances at the end of date: "
            + "assets (current / other / fixed), liabilities (current / long-term), equity incl. current-year profit "
            + "not yet closed. difference = assets - (liabilities + equity); balanced = difference is 0.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Date not in the financial year (ACC_077), "
            + "super admin without companyId (ACC_001), validation (COMMON)"))
    @PreAuthorize(CLOSE)
    @PostMapping
    public ApiMessageDto<SheetDto> generate(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody GenerateForm form) {
        return ApiMessageDto.ok(service.generate(companyId, form));
    }

    @Operation(summary = "Balance sheets", description = "Filters financialYear and status; sections is null here.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(VIEW)
    @GetMapping
    public ApiMessageDto<ResponseListDto<SheetDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer financialYear,
            @RequestParam(required = false) EBalanceSheetStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "sheetDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, financialYear, status, pageable)));
    }

    @Operation(summary = "Compare two finalised balance sheets", description = "Per line both amounts and change = "
            + "second - first, plus section totals.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not both finalised or not one company "
            + "(ACC_074)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(VIEW)
    @GetMapping("/compare")
    public ApiMessageDto<CompareDto> compare(@RequestParam Long firstId, @RequestParam Long secondId) {
        return ApiMessageDto.ok(service.compare(firstId, secondId));
    }

    @Operation(summary = "Balance sheet with sections")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}")
    public ApiMessageDto<SheetDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Balance sheet PDF")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        SheetDto s = service.get(id);
        return LedgerReportController.pdf(LedgerPdf.balanceSheet(s), "balance-sheet-" + s.date() + ".pdf");
    }

    @Operation(summary = "Regenerate a draft", description = "Recomputes the lines from the current books for the "
            + "sheet's date.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(CLOSE)
    @PostMapping("/{id}/regenerate")
    public ApiMessageDto<SheetDto> regenerate(@PathVariable Long id) {
        return ApiMessageDto.ok(service.regenerate(id));
    }

    @Operation(summary = "Replace the notes of a draft", description = "PUT is full replacement of the notes.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(CLOSE)
    @PutMapping("/{id}/notes")
    public ApiMessageDto<SheetDto> notes(@PathVariable Long id, @Valid @RequestBody NotesForm form) {
        return ApiMessageDto.ok(service.updateNotes(id, form));
    }

    @Operation(summary = "Delete a draft")
    @ApiResponses({@ApiResponse(responseCode = "400", description = NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(CLOSE)
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Finalise a draft", description = "DRAFT -> FINALISED; locked afterwards.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(CLOSE)
    @PostMapping("/{id}/finalise")
    public ApiMessageDto<SheetDto> finalise(@PathVariable Long id) {
        return ApiMessageDto.ok(service.finalise(id));
    }
}
