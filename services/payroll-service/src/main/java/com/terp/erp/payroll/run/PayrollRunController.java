package com.terp.erp.payroll.run;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.run.PayrollRunExcelService.ImportResult;
import com.terp.erp.payroll.run.PayrollRunService.EntryDto;
import com.terp.erp.payroll.run.PayrollRunService.RunDto;
import com.terp.erp.payroll.run.PayrollRunService.RunForm;
import com.terp.erp.payroll.run.PayrollRunService.StatusCounts;

import java.nio.charset.StandardCharsets;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Payroll runs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll/runs")
@SecurityRequirement(name = "bearerAuth")
public class PayrollRunController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String NOT_FOUND = "No such payroll run in your company (PAY_012)";
    private static final String NOT_DRAFT = "The run is not DRAFT (PAY_013)";
    private static final String FORM_ERRORS = "Period reversed or longer than 62 days (PAY_016), pay date before the "
            + "period (PAY_017)";

    private final PayrollRunService service;
    private final PayrollRunExcelService excel;

    @Operation(summary = "Payroll runs of the company", description = "keyword = the title; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<RunDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "periodStart", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Draft, Processing, Completed, Cancelled)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword));
    }

    @Operation(summary = "Export the payroll runs (.xlsx)", description = "Same filters as the list, all pages.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status) {
        return file(excel.export(companyId, keyword, status), "payroll-runs.xlsx");
    }

    @Operation(summary = "Import template (.xlsx)",
            description = "Columns: Title, Frequency (MONTHLY, SEMI_MONTHLY, BI_WEEKLY, WEEKLY), Pay Period Start, "
                    + "Pay Period End, Pay Date, Notes.")
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return file(excel.template(), "payroll-runs-import-template.xlsx");
    }

    @Operation(summary = "Import payroll runs (.xlsx, at most 500 rows, 5 MB)",
            description = "Each row becomes a DRAFT run. Rows with errors are reported and skipped; the others are "
                    + "saved.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a workbook (PAY_019), too many rows (PAY_020), "
            + "file too large (PAY_022), super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<ImportResult> importFile(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(excel.importFile(companyId, file));
    }

    @Operation(summary = "Payroll run details", description = "The summary cards (employees, gross pay, total "
            + "deductions, net pay) and the summary bar. Gross pay = total earnings (basic + earning components) − "
            + "LOP deduction − unpaid leave deduction + overtime amount; LOP deduction = basic / working days × LOP "
            + "days (unpaid leave alike); net pay = gross pay − deduction components.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<RunDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Payroll Run", description = "Created as DRAFT.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = FORM_ERRORS + ", super admin without companyId "
            + "(PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping
    public ApiMessageDto<RunDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody RunForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a payroll run", description = "Full replacement; DRAFT runs only.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<RunDto> update(@PathVariable Long id, @Valid @RequestBody RunForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Process a payroll run", description = "DRAFT runs only. Calculates an entry for every "
            + "employee with an ACTIVE salary, from the attendance of the pay period (attendance-service, as you: "
            + "needs ATTENDANCE_VIEW too), and completes the run. The entries are a snapshot: later salary or "
            + "attendance changes do not alter them.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = NOT_DRAFT + ", no employee with an active salary "
                    + "(PAY_015)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "attendance-service unavailable (PAY_011)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping("/{id}/process")
    public ApiMessageDto<RunDto> process(@PathVariable Long id) {
        return ApiMessageDto.ok(service.process(id));
    }

    @Operation(summary = "Cancel a payroll run", description = "A DRAFT or COMPLETED run; its entries are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already cancelled (PAY_023)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}/cancel")
    public ApiMessageDto<RunDto> cancel(@PathVariable Long id) {
        return ApiMessageDto.ok(service.cancel(id));
    }

    @Operation(summary = "Delete a payroll run", description = "DRAFT runs only.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = NOT_DRAFT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Employee Payroll Entries of a run", description = "By employee name. Needs EMPLOYEE_VIEW "
            + "too (photos come from employee-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}/entries")
    public ApiMessageDto<ResponseListDto<EntryDto>> entries(@PathVariable Long id,
                                                            @ParameterObject @PageableDefault(size = 20)
                                                            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.entries(id, pageable)));
    }

    @Operation(summary = "Details of an entry", description = "The full breakdown with each component.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + ", no such entry in the run "
            + "(PAY_018)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}/entries/{entryId}")
    public ApiMessageDto<EntryDto> entry(@PathVariable Long id, @PathVariable Long entryId) {
        return ApiMessageDto.ok(service.entry(id, entryId));
    }

    @Operation(summary = "Remove an entry from a run", description = "COMPLETED runs only; the run totals are "
            + "recalculated and returned.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The run is not COMPLETED (PAY_014)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", no such entry in the run (PAY_018)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/{id}/entries/{entryId}")
    public ApiMessageDto<RunDto> deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        return ApiMessageDto.ok(service.deleteEntry(id, entryId));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
