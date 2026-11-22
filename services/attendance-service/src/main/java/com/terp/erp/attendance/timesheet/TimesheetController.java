package com.terp.erp.attendance.timesheet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.attendance.timesheet.TimesheetDtos.DecisionForm;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryDto;
import com.terp.erp.attendance.timesheet.TimesheetDtos.EntryForm;
import com.terp.erp.attendance.timesheet.TimesheetDtos.Month;
import com.terp.erp.attendance.timesheet.TimesheetDtos.ImportResult;
import com.terp.erp.attendance.timesheet.TimesheetDtos.Week;
import com.terp.erp.common.ApiMessageDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Timesheet")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/timesheets")
@SecurityRequirement(name = "bearerAuth")
public class TimesheetController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String SAVE_ERRORS = "Day over 24 h (ATT_031), future date (ATT_032), hours outside 0.25–24 "
            + "(COMMON_001)";

    private final TimesheetService service;
    private final TimesheetFileService files;

    @Operation(summary = "Weekly grid (Mon–Sun) of the week containing date",
            description = "One row per employee (paged by employee number; keyword = employee search), one cell per day "
                    + "with hours, entry count and the day's status (PENDING if any entry waits, else REJECTED if any "
                    + "was rejected, else APPROVED), and the week's total. counts = the status tabs of that week. "
                    + "status filters the entries. Needs EMPLOYEE_VIEW too.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/week")
    public ApiMessageDto<Week> week(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ERegularizationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiMessageDto.ok(service.week(companyId, date, employeeId, keyword, status, page, size));
    }

    @Operation(summary = "Monthly grid of the month (ERP manual 11.5)",
            description = "One row per employee (paged; keyword = employee search; employeeId = one user), one cell per "
                    + "day of the month with hours, entry count and the day's status; row, day and month totals. status "
                    + "filters the entries. Needs EMPLOYEE_VIEW too.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004), month "
            + "or year out of range (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/month")
    public ApiMessageDto<Month> month(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @Min(2000) @Max(2100) int year, @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ERegularizationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiMessageDto.ok(service.month(companyId, year, month, employeeId, keyword, status, page, size));
    }

    @Operation(summary = "Every entry of one employee's day (View All)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/entries")
    public ApiMessageDto<List<EntryDto>> day(@RequestParam Long employeeId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiMessageDto.ok(service.day(employeeId, date));
    }

    @Operation(summary = "Timesheet entry details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such entry in your company (ATT_029)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/entries/{id}")
    public ApiMessageDto<EntryDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a timesheet entry", description = "Starts PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = SAVE_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping("/entries")
    public ApiMessageDto<EntryDto> create(@Valid @RequestBody EntryForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a pending or rejected entry (full replacement)",
            description = "Omitted project is cleared; the entry goes back to PENDING. Approved entries are final.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = SAVE_ERRORS + ", approved (ATT_030)"),
            @ApiResponse(responseCode = "404", description = "No such entry (ATT_029) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/entries/{id}")
    public ApiMessageDto<EntryDto> update(@PathVariable Long id, @Valid @RequestBody EntryForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Approve a pending entry")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such entry in your company (ATT_029)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/entries/{id}/approve")
    public ApiMessageDto<EntryDto> approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.approve(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Reject a pending entry")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such entry in your company (ATT_029)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/entries/{id}/reject")
    public ApiMessageDto<EntryDto> reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.reject(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Delete an entry")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such entry in your company (ATT_029)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/entries/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Import template (.xlsx)", description = "Columns: Employee, Date, Hours, Project, Description.")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return file(files.template(), "timesheet-import-template.xlsx");
    }

    @Operation(summary = "Import timesheet entries (CSV or .xlsx, at most 2000 rows, 5 MB)",
            description = "Employee = the exact full name, or the Employee Code / Employee No. Each row creates a "
                    + "PENDING entry; rows with errors are reported and skipped.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Unreadable file (ATT_013), too many rows (ATT_014), "
            + "file too large (ATT_016), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<ImportResult> importFile(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(files.importFile(companyId, file));
    }

    @Operation(summary = "Export entries of [from, to] (.xlsx, at most a year)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad range (COMMON_001), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) ERegularizationStatus status) {
        return file(files.export(companyId, from, to, employeeId, status), "timesheet-%s-%s.xlsx".formatted(from, to));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
