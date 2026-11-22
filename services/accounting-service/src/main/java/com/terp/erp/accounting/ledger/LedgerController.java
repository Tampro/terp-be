package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.ledger.AccountMapping.MappingDto;
import com.terp.erp.accounting.ledger.AccountMapping.MappingForm;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final AccountMapping mapping;
    private final JournalService journal;

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

    @Operation(summary = "Journal entries", description = "Read-only; filters sourceType (REVENUE, EXPENSE, "
            + "BANK_TRANSFER, POS_ORDER ...) and date range. lines is null in this list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('COA_VIEW')")
    @GetMapping("/journal-entries")
    public ApiMessageDto<ResponseListDto<JournalEntryDto>> entries(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "entryDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(journal.search(companyId, sourceType, from, to, pageable)));
    }

    @Operation(summary = "Journal entry with its lines")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such journal entry in your company "
            + "(ACC_021)"))
    @PreAuthorize("@PermissionChecker.has('COA_VIEW')")
    @GetMapping("/journal-entries/{id}")
    public ApiMessageDto<JournalEntryDto> entry(@PathVariable Long id) {
        return ApiMessageDto.ok(journal.get(id));
    }
}
