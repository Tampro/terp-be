package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.ledger.YearEndCloseService.CloseDto;
import com.terp.erp.accounting.ledger.YearEndCloseService.CloseForm;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Year-end close")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/year-end-closes")
@SecurityRequirement(name = "bearerAuth")
public class YearEndCloseController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final YearEndCloseService service;

    @Operation(summary = "Close a financial year", description = "Posts one closing entry (sourceType "
            + "YEAR_END_CLOSE): revenue and expense balances to 911, the net to 4212 (account mapping "
            + "INCOME_SUMMARY / RETAINED_EARNINGS). Once per year and cannot be undone; afterwards no entry may be "
            + "dated on or before 31 December of that year. The next year opens with the carried balances.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Closing date not in the year (ACC_077), already "
            + "closed (ACC_075), draft entries in the year (ACC_076), a later year closed (ACC_070), 911 / 4212 not "
            + "mapped (ACC_017), inactive account with a balance (ACC_020), super admin without companyId "
            + "(ACC_001), validation (COMMON)"))
    @PreAuthorize("@PermissionChecker.has('LEDGER_CLOSE')")
    @PostMapping
    public ApiMessageDto<CloseDto> close(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CloseForm form) {
        return ApiMessageDto.ok(service.close(companyId, form));
    }

    @Operation(summary = "Closed financial years")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('LEDGER_CLOSE','LEDGER_REPORT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CloseDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "fiscalYear", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, pageable)));
    }
}
