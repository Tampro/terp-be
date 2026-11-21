package com.terp.erp.payroll.payslip;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.payroll.payslip.Payslip.EStatus;
import com.terp.erp.payroll.payslip.PayslipService.GenerateResult;
import com.terp.erp.payroll.payslip.PayslipService.PayslipDto;
import com.terp.erp.payroll.payslip.PayslipService.Pdf;
import com.terp.erp.payroll.payslip.PayslipService.StatusCounts;

import java.nio.charset.StandardCharsets;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Payslips")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll")
@SecurityRequirement(name = "bearerAuth")
public class PayslipController {
    private static final String KEYWORD = "Matches the employee's name, no. or email";
    private static final String NOT_FOUND = "No such payslip in your company (PAY_026)";

    private final PayslipService service;

    @Operation(summary = "Generate Payslips of a run", description = "COMPLETED runs only. One payslip per entry that "
            + "has none yet (running it again only adds the missing ones). Afterwards the run can no longer be "
            + "cancelled nor its entries removed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The run is not COMPLETED (PAY_024)"),
            @ApiResponse(responseCode = "404", description = "No such payroll run in your company (PAY_012)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping("/runs/{id}/generate-payslips")
    public ApiMessageDto<GenerateResult> generate(@PathVariable Long id) {
        return ApiMessageDto.ok(service.generate(id));
    }

    @Operation(summary = "Payslips of the company", description = "year + month = the month tab (the month the pay "
            + "period starts in); employeeId = the Employee filter; status = the tab. Needs EMPLOYEE_VIEW too "
            + "(photos come from employee-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/payslips")
    public ApiMessageDto<ResponseListDto<PayslipDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) EStatus status,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, year, month, employeeId, status, keyword,
                pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Generated, Downloaded)", description = "Same filters as "
            + "the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/payslips/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
            @RequestParam(required = false) @Min(1) @Max(12) Integer month,
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, year, month, employeeId, keyword));
    }

    @Operation(summary = "View a payslip", description = "With the full breakdown of its payroll entry (entry).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/payslips/{id}")
    public ApiMessageDto<PayslipDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Download the payslip PDF", description = "Marks the payslip DOWNLOADED.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/payslips/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Pdf pdf = service.pdf(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.bytes());
    }
}
