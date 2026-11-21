package com.terp.erp.attendance.biometric;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.biometric.BiometricDtos.DayCount;
import com.terp.erp.attendance.biometric.BiometricDtos.DayPage;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchBatch;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchLog;
import com.terp.erp.attendance.biometric.BiometricDtos.PunchResult;
import com.terp.erp.attendance.biometric.BiometricDtos.SyncForm;
import com.terp.erp.attendance.biometric.BiometricDtos.SyncResult;
import com.terp.erp.attendance.biometric.DeviceKeyFilter.DevicePrincipal;
import com.terp.erp.common.ApiMessageDto;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Biometric Attendance")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/biometric")
public class BiometricAttendanceController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final BiometricService service;

    @Operation(summary = "Device push: punches of a biometric device (no Keycloak token)",
            description = "Authenticated by the device key (header X-Device-Key), not by a token. At most 500 punches. "
                    + "employeeCode = the employee's Employee Code, else Employee No; unknown codes are kept. A punch "
                    + "with an external id already received from this device is skipped (safe to re-send).")
    @Parameter(name = DeviceKeyFilter.HEADER, in = ParameterIn.HEADER, required = true,
            description = "Key shown when the device was registered or its key rotated")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "0 or more than 500 punches (ATT_025), bad body (COMMON_001)"),
            @ApiResponse(responseCode = "401", description = "Missing, unknown or inactive device key (ATT_024)")})
    @PostMapping("/punches")
    public ApiMessageDto<PunchResult> punches(@AuthenticationPrincipal DevicePrincipal device,
                                              @Valid @RequestBody PunchBatch batch) {
        return ApiMessageDto.ok(service.receive(device, batch));
    }

    @Operation(summary = "Biometric attendance of a day: first in, last out and punch count per person",
            description = "keyword matches the employee's name or the device user id. Rows of unknown device user ids "
                    + "have employee = null. totalRecords = people with punches that day. Needs EMPLOYEE_VIEW too.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/attendance")
    public ApiMessageDto<DayPage> day(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiMessageDto.ok(service.day(companyId, date, keyword, employeeId, page, size));
    }

    @Operation(summary = "People with punches per day of [from, to] (date navigator, at most 42 days)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad range (COMMON_001), no company (ATT_004)"))
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/attendance/day-counts")
    public ApiMessageDto<List<DayCount>> dayCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.dayCounts(companyId, from, to));
    }

    @Operation(summary = "Full punch log of one person's day (View)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/attendance/log")
    public ApiMessageDto<List<PunchLog>> log(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String employeeCode) {
        return ApiMessageDto.ok(service.log(companyId, date, employeeCode));
    }

    @Operation(summary = "Sync one person's day into the attendance record",
            description = "First punch = clock-in, last punch = clock-out (one punch = clock-in only). An existing "
                    + "record's times are overwritten (notes and holiday kept); flags are computed again.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "No punches (ATT_026), unknown device user id "
            + "(ATT_027), future date (ATT_011), no company (ATT_004)"))
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping("/attendance/sync")
    public ApiMessageDto<SyncResult> sync(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody SyncForm form) {
        return ApiMessageDto.ok(service.sync(companyId, form));
    }
}
