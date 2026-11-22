package com.terp.erp.attendance.regularization;

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
import com.terp.erp.attendance.regularization.RegularizationDtos.DecisionForm;
import com.terp.erp.attendance.regularization.RegularizationDtos.RegularizationDto;
import com.terp.erp.attendance.regularization.RegularizationDtos.RegularizationForm;
import com.terp.erp.attendance.regularization.RegularizationDtos.StatusCounts;
import com.terp.erp.attendance.regularization.RegularizationDtos.Summary;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.time.LocalDate;

@Tag(name = "Attendance Regularizations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/regularizations")
@SecurityRequirement(name = "bearerAuth")
public class RegularizationController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the employee's name, number, code or email, or the reason";
    private static final String FORM_ERRORS = "No requested time (ATT_022), record of another employee (ATT_021), a "
            + "pending request already exists for the record (ATT_019)";

    private final RegularizationService service;

    @Operation(summary = "Regularization requests (cards)",
            description = "dateFrom/dateTo filter the attendance date. Sort by date (workDate), createdDate, status. "
                    + "Needs EMPLOYEE_VIEW too: employee names come from employee-service as the caller.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<RegularizationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ERegularizationStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, employeeId, dateFrom, dateTo, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, Approved, Rejected)",
            description = "Same filters as the list, without status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId, dateFrom, dateTo));
    }

    @Operation(summary = "Summary cards: Total, Pending, Approved (with approval rate), Rejected — all time",
            description = "approvalRate = approved / (approved + rejected), in percent.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/summary")
    public ApiMessageDto<Summary> summary(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.summary(companyId));
    }

    @Operation(summary = "Regularization request details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such request in your company (ATT_018)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<RegularizationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a regularization request",
            description = "The original times are copied from the chosen record. A requested time left empty keeps "
                    + "the record's time on approval.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such record in your company (ATT_008)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<RegularizationDto> create(@Valid @RequestBody RegularizationForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a pending request (full replacement)",
            description = "Omitted requested times are cleared; the original times are copied again from the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such request (ATT_018) or record (ATT_008)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<RegularizationDto> update(@PathVariable Long id, @Valid @RequestBody RegularizationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Approve a pending request",
            description = "Writes the requested times into the attendance record and computes its flags again.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020), clock-out without clock-in "
                    + "(ATT_010)"),
            @ApiResponse(responseCode = "404", description = "No such request in your company (ATT_018)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}/approve")
    public ApiMessageDto<RegularizationDto> approve(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.approve(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Reject a pending request", description = "The attendance record stays as it is.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such request in your company (ATT_018)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}/reject")
    public ApiMessageDto<RegularizationDto> reject(@PathVariable Long id,
                                                   @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(service.reject(id, form == null ? null : form.comments()));
    }

    @Operation(summary = "Delete a pending request permanently", description = "Decided requests stay.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (ATT_020)"),
            @ApiResponse(responseCode = "404", description = "No such request in your company (ATT_018)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
