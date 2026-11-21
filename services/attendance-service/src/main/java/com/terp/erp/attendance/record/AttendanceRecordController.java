package com.terp.erp.attendance.record;

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
import com.terp.erp.attendance.record.RecordDtos.Calendar;
import com.terp.erp.attendance.record.RecordDtos.ImportResult;
import com.terp.erp.attendance.record.RecordDtos.RecordDto;
import com.terp.erp.attendance.record.RecordDtos.RecordForm;
import com.terp.erp.common.ApiMessageDto;

import java.nio.charset.StandardCharsets;

@Tag(name = "Attendance Records")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/records")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceRecordController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String RECORD_RULES = "Clock-out without clock-in (ATT_010), clock times on a future date "
            + "(ATT_011), the employee already has a record that day (ATT_009)";

    private final AttendanceRecordService service;
    private final AttendanceExcelService excel;

    @Operation(summary = "Attendance calendar of a month: one row per employee, one cell per day",
            description = "Cell status: PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HOLIDAY, DAY_OFF, FUTURE, NOT_ADDED, with "
                    + "late / earlyDeparture / overtime flags and the clock times for the hover. Employees are paged "
                    + "(by employee number); employeeId shows one. Needs EMPLOYEE_VIEW and ORG_VIEW too (employees "
                    + "and holidays are read from employee-service as the caller).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (ATT_012)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<Calendar> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiMessageDto.ok(service.calendar(companyId, year, month, employeeId, page, size));
    }

    @Operation(summary = "Records of one employee, newest first",
            description = "For choosing the record of a regularization request.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<com.terp.erp.common.ResponseListDto<RecordDto>> ofEmployee(
            @RequestParam Long employeeId,
            @org.springdoc.core.annotations.ParameterObject
            @org.springframework.data.web.PageableDefault(size = 31) org.springframework.data.domain.Pageable pageable) {
        return ApiMessageDto.ok(com.terp.erp.common.ResponseListDto.of(service.ofEmployee(employeeId, pageable)));
    }

    @Operation(summary = "Attendance record details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such record in your company (ATT_008)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<RecordDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an attendance record",
            description = "No clock-in = Absent. Late, Early Departure, Overtime and Half Day are computed from the "
                    + "employee's shift and attendance policy (from the profile).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RECORD_RULES),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<RecordDto> create(@Valid @RequestBody RecordForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an attendance record (full replacement)",
            description = "Omitted times and notes are cleared; flags are computed again with the current shift/policy.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RECORD_RULES),
            @ApiResponse(responseCode = "404", description = "No such record (ATT_008) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<RecordDto> update(@PathVariable Long id, @Valid @RequestBody RecordForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an attendance record", description = "The day shows Not Added again.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such record in your company (ATT_008)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Import template (.xlsx)",
            description = "Columns: Employee Code or Email, Date, Clock In, Clock Out, Holiday (Y/N), Notes.")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return file(excel.template(), "attendance-import-template.xlsx");
    }

    @Operation(summary = "Import attendance records (.xlsx, at most 2000 rows, 5 MB)",
            description = "A row updates the record of that employee and date when one exists, else creates it. "
                    + "Rows with errors are reported and skipped; the others are saved.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a workbook (ATT_013), too many rows (ATT_014), "
            + "file too large (ATT_016), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<ImportResult> importFile(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(excel.importFile(companyId, file));
    }

    @Operation(summary = "Export the month calendar (.xlsx)",
            description = "One row per employee, one column per day with a code (P, A, HD, LV, H, OFF, -; +L late, "
                    + "+E early departure, +OT overtime), then Attended and Working days.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month,
            @RequestParam(required = false) Long employeeId) {
        return file(excel.export(companyId, year, month, employeeId),
                "attendance-%d-%02d.xlsx".formatted(year, month));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
