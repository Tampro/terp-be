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
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeDto;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeForm;
import com.terp.erp.attendance.leave.LeaveDtos.LeaveTypeOption;
import com.terp.erp.attendance.leave.LeaveDtos.StatusForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.util.List;

@Tag(name = "Leave Types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/leave/types")
@SecurityRequirement(name = "bearerAuth")
public class LeaveTypeController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final LeaveTypeService service;

    @Operation(summary = "Leave types of the company",
            description = "keyword matches the name; status = ACTIVE / INACTIVE (All Statuses = omitted). Sort by name, "
                    + "maxDaysPerYear, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<LeaveTypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Active leave types for dropdowns and the calendar legend")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.hasAny('LEAVE_VIEW', 'ATTENDANCE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<LeaveTypeOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Leave type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such leave type in your company (ATT_034)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<LeaveTypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a leave type", description = "Omitted paid = true, color = #3B82F6, status = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (ATT_003), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PostMapping
    public ApiMessageDto<LeaveTypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                              @Valid @RequestBody LeaveTypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a leave type (full replacement)",
            description = "Omitted description is cleared, omitted paid = true, color = #3B82F6; omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (ATT_003)"),
            @ApiResponse(responseCode = "404", description = "No such leave type in your company (ATT_034)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<LeaveTypeDto> update(@PathVariable Long id, @Valid @RequestBody LeaveTypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a leave type")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such leave type in your company (ATT_034)"))
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<LeaveTypeDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a leave type", description = "Refused while a leave policy uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (ATT_038)"),
            @ApiResponse(responseCode = "404", description = "No such leave type in your company (ATT_034)")})
    @PreAuthorize("@PermissionChecker.has('LEAVE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
