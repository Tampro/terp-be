package com.terp.erp.attendance.leave;

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
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationDto;
import com.terp.erp.attendance.leave.LeaveAppDtos.ApplicationForm;
import com.terp.erp.attendance.leave.LeaveAppDtos.Calendar;
import com.terp.erp.attendance.leave.LeaveAppDtos.DecisionForm;
import com.terp.erp.attendance.regularization.ERegularizationStatus;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "Leave Applications")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/leave/applications")
@SecurityRequirement(name = "bearerAuth")
public class LeaveApplicationController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the employee's name, email, number or code";
    private static final String RULES = "Inactive type (ATT_040), end before start (ATT_041), across years (ATT_042), "
            + "no working day (ATT_043), outside the policy's days per application (ATT_044), overlapping leave "
            + "(ATT_045), no balance synced (ATT_046), not enough balance (ATT_047), attachment not in the media "
            + "library (ATT_049)";

    private final LeaveApplicationService service;
    private final LeaveExportService export;

    @Operation(summary = "Leave applications (Pending / Rejected tabs, or any status)",
            description = "year keeps the leaves overlapping that year. Sort by startDate, createdDate. Needs "
                    + "EMPLOYEE_VIEW too: employees are read from employee-service as the caller.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ApplicationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ERegularizationStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) Integer year,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, status, employeeId, leaveTypeId, year, keyword, pageable)));
    }

    @Operation(summary = "Leave calendar of [from, to] (at most 42 days)",
            description = "One row per employee with a pending or approved leave in the range, each leave with its "
                    + "type colour. The legend = GET /attendance/leave/types/options.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad range (COMMON_001), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<Calendar> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to, employeeId, keyword));
    }

    @Operation(summary = "Export leave applications (.xlsx, same filters as the list)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) ERegularizationStatus status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) Integer year,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword) {
        byte[] body = export.export(companyId, status, employeeId, leaveTypeId, year, keyword);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(year == null ? "leaves.xlsx" : "leaves-" + year + ".xlsx", StandardCharsets.UTF_8)
                        .build().toString())
                .body(body);
    }

    @Operation(summary = "Leave application details", description = "Attachment link only for callers with MEDIA_VIEW.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such application in your company (ATT_039)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ApplicationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a leave application",
            description = "Days = working days of the range. Without approval required by the type's policy it is "
                    + "approved at once (balance used, attendance marked ON_LEAVE); otherwise PENDING, its days "
                    + "reserved on the balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES),
            @ApiResponse(responseCode = "404", description = "No such employee (EMP_001) or leave type (ATT_034)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ApplicationDto> create(@Valid @RequestBody ApplicationForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a pending or rejected application (full replacement)",
            description = "Omitted attachment is cleared; it goes back to PENDING. Approved ones are final.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES + ", approved (ATT_048)"),
            @ApiResponse(responseCode = "404", description = "No such application (ATT_039), employee or leave type")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ApplicationDto> update(@PathVariable Long id, @Valid @RequestBody ApplicationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Approve a pending application",
            description = "Uses the balance and marks every working day ON_LEAVE (a day with clock times is kept).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020), not enough balance (ATT_047)"),
            @ApiResponse(responseCode = "404", description = "No such application in your company (ATT_039)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}/approve")
    public ApiMessageDto<ApplicationDto> approve(@PathVariable Long id,
                                                 @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.approve(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Reject a pending application", description = "Its reserved days are released.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such application in your company (ATT_039)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}/reject")
    public ApiMessageDto<ApplicationDto> reject(@PathVariable Long id,
                                                @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.reject(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Delete a pending or rejected application")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Approved (ATT_048)"),
            @ApiResponse(responseCode = "404", description = "No such application in your company (ATT_039)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
