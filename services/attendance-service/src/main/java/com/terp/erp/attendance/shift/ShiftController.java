package com.terp.erp.attendance.shift;

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
import com.terp.erp.attendance.shift.ShiftDtos.ShiftDto;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftForm;
import com.terp.erp.attendance.shift.ShiftDtos.ShiftOption;
import com.terp.erp.attendance.shift.ShiftDtos.StatusCounts;
import com.terp.erp.attendance.shift.ShiftDtos.StatusForm;
import com.terp.erp.attendance.shift.ShiftDtos.Summary;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.util.List;

@Tag(name = "Shifts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/shifts")
@SecurityRequirement(name = "bearerAuth")
public class ShiftController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NIGHT = "Shift Type: true = Night Shift, false = Day Shift, omitted = both";
    private static final String RULES = "End not after start without nightShift (ATT_005), break start/end not both "
            + "given or outside the shift (ATT_006), break as long as the shift (ATT_007), name taken (ATT_003)";

    private final ShiftService service;

    @Operation(summary = "Shifts of the company",
            description = "keyword matches name or description. Sort by name, startTime, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ShiftDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @Parameter(description = NIGHT) @RequestParam(required = false) Boolean nightShift,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, nightShift, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)",
            description = "Same keyword and Shift Type filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @Parameter(description = NIGHT) @RequestParam(required = false) Boolean nightShift) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, nightShift));
    }

    @Operation(summary = "Summary cards: Total, Active, Night and Day shifts")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/summary")
    public ApiMessageDto<Summary> summary(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.summary(companyId));
    }

    @Operation(summary = "Active shifts for dropdowns (employee profile)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.hasAny('ATTENDANCE_VIEW', 'EMPLOYEE_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<ShiftOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Shift details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such shift in your company (ATT_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ShiftDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a shift", description = "status omitted = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = RULES + ", no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ShiftDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody ShiftForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a shift (full replacement)",
            description = "Every field is replaced (omitted description and break times = cleared); omitted status "
                    + "keeps the current one.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES),
            @ApiResponse(responseCode = "404", description = "No such shift in your company (ATT_001)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ShiftDto> update(@PathVariable Long id, @Valid @RequestBody ShiftForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a shift")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such shift in your company (ATT_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ShiftDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a shift")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such shift in your company (ATT_001)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
