package com.terp.erp.attendance.setting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.setting.AttendanceSettingService.SettingsDto;
import com.terp.erp.attendance.setting.AttendanceSettingService.SettingsForm;
import com.terp.erp.common.ApiMessageDto;

@Tag(name = "Attendance Settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/settings")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceSettingController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final AttendanceSettingService service;

    @Operation(summary = "Weekly days off of the company", description = "Default SATURDAY and SUNDAY.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<SettingsDto> get(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Set the weekly days off (full replacement)",
            description = "Applies to the calendar from now on; saved records keep their weekend flag.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not 1 to 6 days (ATT_017), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping
    public ApiMessageDto<SettingsDto> save(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }
}
