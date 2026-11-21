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
import com.terp.erp.attendance.leave.LeaveDtos.LeavePolicyDto;
import com.terp.erp.attendance.leave.LeaveDtos.LeavePolicyForm;
import com.terp.erp.attendance.leave.LeaveDtos.StatusCounts;
import com.terp.erp.attendance.leave.LeaveDtos.StatusForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

@Tag(name = "Leave Policies")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/leave/policies")
@SecurityRequirement(name = "bearerAuth")
public class LeavePolicyController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String RULES = "Min above max (ATT_037), the leave type already has an active policy "
            + "(ATT_036), name taken (ATT_003)";

    private final LeavePolicyService service;

    @Operation(summary = "Leave policies of the company",
            description = "keyword matches name or description; leaveTypeId and status filter. Sort by name, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<LeavePolicyDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, leaveTypeId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)",
            description = "Same keyword and leave type filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long leaveTypeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, leaveTypeId));
    }

    @Operation(summary = "Leave policy details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_035)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<LeavePolicyDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a leave policy", description = "Omitted requiresApproval = true, status = ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES),
            @ApiResponse(responseCode = "404", description = "No such leave type in your company (ATT_034)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PostMapping
    public ApiMessageDto<LeavePolicyDto> create(@Valid @RequestBody LeavePolicyForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a leave policy (full replacement)",
            description = "Omitted description is cleared, omitted requiresApproval = true; omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES),
            @ApiResponse(responseCode = "404", description = "No such policy (ATT_035) or leave type (ATT_034)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<LeavePolicyDto> update(@PathVariable Long id, @Valid @RequestBody LeavePolicyForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a leave policy")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The leave type already has an active policy (ATT_036)"),
            @ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_035)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<LeavePolicyDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a leave policy")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such policy in your company (ATT_035)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
