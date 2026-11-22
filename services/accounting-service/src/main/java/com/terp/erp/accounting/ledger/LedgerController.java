package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.ledger.AccountMapping.MappingDto;
import com.terp.erp.accounting.ledger.AccountMapping.MappingForm;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
import com.terp.erp.accounting.ledger.ManualJournalService.ManualForm;
import com.terp.erp.accounting.ledger.ManualJournalService.ReverseForm;
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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Ledger")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting")
@SecurityRequirement(name = "bearerAuth")
public class LedgerController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private static final String READ = "@PermissionChecker.hasAny('COA_VIEW','LEDGER_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('LEDGER_MANAGE')";

    private final AccountMapping mapping;
    private final JournalService journal;
    private final ManualJournalService manual;

    @Operation(summary = "System account mapping", description = "Every system key with its account; the first "
            + "call seeds the default VAS TT200 chart.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('COA_VIEW')")
    @GetMapping("/account-mapping")
    public ApiMessageDto<List<MappingDto>> getMapping(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(mapping.get(companyId));
    }

    @Operation(summary = "Replace the system account mapping", description = "Full replacement: every key must "
            + "map to an active account of the company. Applies to postings made after the change.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "A key missing or mapped to an inactive / "
            + "unknown account (ACC_017), super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('COA_MANAGE')")
    @PutMapping("/account-mapping")
    public ApiMessageDto<List<MappingDto>> putMapping(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody MappingForm form) {
        return ApiMessageDto.ok(mapping.update(companyId, form));
    }

    @Operation(summary = "Journal entries", description = "Filters sourceType (REVENUE, EXPENSE, BANK_TRANSFER, "
            + "POS_ORDER, REVERSAL, YEAR_END_CLOSE ...), type (AUTOMATIC / MANUAL), status and date range. lines is "
            + "null in this list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(READ)
    @GetMapping("/journal-entries")
    public ApiMessageDto<ResponseListDto<JournalEntryDto>> entries(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) EJournalType type,
            @RequestParam(required = false) EJournalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "entryDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(journal.search(companyId, sourceType, type, status, from, to,
                pageable)));
    }

    @Operation(summary = "Journal entry with its lines")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such journal entry in your company "
            + "(ACC_021)"))
    @PreAuthorize(READ)
    @GetMapping("/journal-entries/{id}")
    public ApiMessageDto<JournalEntryDto> entry(@PathVariable Long id) {
        return ApiMessageDto.ok(journal.get(id));
    }

    @Operation(summary = "Create a manual journal entry", description = "DRAFT (post=false: no balance moves) or "
            + "POSTED (post=true). Date defaults to today; description at least 5 characters; each line has exactly "
            + "one of debit / credit > 0; total debit must equal total credit. Numbered JE-YYYY-NNN like automatic "
            + "entries.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Unbalanced, args debit / credit / difference "
            + "(ACC_066), bad line (ACC_019), account inactive or of another company (ACC_020), date in a closed "
            + "year (ACC_070), super admin without companyId (ACC_001), validation (COMMON)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/journal-entries")
    public ApiMessageDto<JournalEntryDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody ManualForm form) {
        return ApiMessageDto.ok(manual.create(companyId, form));
    }

    @Operation(summary = "Replace a draft manual entry", description = "PUT is full replacement of a DRAFT manual "
            + "entry (date, reference type, reference, description, every line); post=true also posts it. The "
            + "number is kept.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (ACC_067), automatic entry "
            + "(ACC_068), unbalanced (ACC_066), bad line (ACC_019), account (ACC_020), closed year (ACC_070)"),
            @ApiResponse(responseCode = "404", description = "No such journal entry in your company (ACC_021)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/journal-entries/{id}")
    public ApiMessageDto<JournalEntryDto> update(@PathVariable Long id, @Valid @RequestBody ManualForm form) {
        return ApiMessageDto.ok(manual.update(id, form));
    }

    @Operation(summary = "Delete a draft manual entry")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (ACC_067), automatic entry "
            + "(ACC_068)"),
            @ApiResponse(responseCode = "404", description = "No such journal entry in your company (ACC_021)")})
    @PreAuthorize(MANAGE)
    @DeleteMapping("/journal-entries/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        manual.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Post a draft manual entry", description = "Moves the account balances; DRAFT -> POSTED.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (ACC_067), automatic entry "
            + "(ACC_068), account inactive (ACC_020), closed year (ACC_070)"),
            @ApiResponse(responseCode = "404", description = "No such journal entry in your company (ACC_021)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/journal-entries/{id}/post")
    public ApiMessageDto<JournalEntryDto> post(@PathVariable Long id) {
        return ApiMessageDto.ok(manual.post(id));
    }

    @Operation(summary = "Reverse a posted entry", description = "Manual or automatic. Creates a POSTED entry with "
            + "debit and credit swapped, dated the given date (default today), sourceType REVERSAL, reversalOfId = "
            + "the original; the original becomes REVERSED with reversedById. Returns the reversal. Body optional.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not posted, already reversed or itself a "
            + "reversal (ACC_069), date before the original (ACC_071), closed year (ACC_070), account inactive "
            + "(ACC_020)"),
            @ApiResponse(responseCode = "404", description = "No such journal entry in your company (ACC_021)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/journal-entries/{id}/reverse")
    public ApiMessageDto<JournalEntryDto> reverse(@PathVariable Long id,
                                                  @Valid @RequestBody(required = false) ReverseForm form) {
        return ApiMessageDto.ok(manual.reverse(id, form));
    }
}
