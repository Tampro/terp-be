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

    @Operation(summary = "Attendance settings of the company",
            description = "Weekly days off (default SATURDAY and SUNDAY; working days are the others) and the IP "
                    + "restriction of self clock-in/out (default off).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<SettingsDto> get(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Save the attendance settings (full replacement)",
            description = "Days off apply to the calendar from now on; saved records keep their weekend flag. "
                    + "Omitted ipRestrictionEnabled / allowedIps = off / none.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not 1 to 6 days (ATT_017), no company (ATT_004), "
            + "invalid IP or CIDR (ATT_057), more than 100 IPs (ATT_058), restriction on without IPs (ATT_059)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping
    public ApiMessageDto<SettingsDto> save(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }
}
