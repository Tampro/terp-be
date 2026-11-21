package com.terp.erp.attendance.policy;

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
import org.springframework.data.web.PageableDefault;
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
import com.terp.erp.attendance.common.EStatus;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyDto;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyForm;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.PolicyOption;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.StatusCounts;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.StatusForm;
import com.terp.erp.attendance.policy.AttendancePolicyDtos.Summary;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.util.List;

@Tag(name = "Attendance Policies")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/policies")
@SecurityRequirement(name = "bearerAuth")
public class AttendancePolicyController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final AttendancePolicyService service;

    @Operation(summary = "Attendance policies of the company",
            description = "keyword matches name or description. Sort by name, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PolicyDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same keyword as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword));
    }

    @Operation(summary = "Summary cards: Total, Active, Avg Late Grace (minutes), Avg Overtime Rate")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/summary")
    public ApiMessageDto<Summary> summary(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.summary(companyId));
    }

    @Operation(summary = "Active policies for dropdowns (employee profile)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.hasAny('ATTENDANCE_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<PolicyOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Attendance policy details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_002)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<PolicyDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an attendance policy",
            description = "Omitted grace periods = 15 min, half-day threshold = 4 h, status = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (ATT_003), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<PolicyDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody PolicyForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an attendance policy (full replacement)",
            description = "Omitted description is cleared, omitted numbers take the defaults, omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (ATT_003)"),
            @ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_002)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<PolicyDto> update(@PathVariable Long id, @Valid @RequestBody PolicyForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate an attendance policy")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_002)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<PolicyDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an attendance policy")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_002)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
