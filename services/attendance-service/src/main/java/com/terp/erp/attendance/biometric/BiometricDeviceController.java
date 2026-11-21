package com.terp.erp.attendance.biometric;

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
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceDto;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceForm;
import com.terp.erp.attendance.biometric.BiometricDtos.DeviceWithKey;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

@Tag(name = "Biometric Devices")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/biometric/devices")
@SecurityRequirement(name = "bearerAuth")
public class BiometricDeviceController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final BiometricService service;

    @Operation(summary = "Biometric devices of the company", description = "Sort by name, createdDate, lastSeenAt.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DeviceDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.devices(companyId, pageable)));
    }

    @Operation(summary = "Biometric device details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such device in your company (ATT_023)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DeviceDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.device(id));
    }

    @Operation(summary = "Register a device; the answer carries its key ONCE",
            description = "Configure apiKey on the device (header X-Device-Key of POST /attendance/biometric/punches). "
                    + "Only its hash is stored: it cannot be shown again, only rotated.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Serial taken (ATT_028), no company (ATT_004)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<DeviceWithKey> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody DeviceForm form) {
        return ApiMessageDto.ok(service.createDevice(companyId, form));
    }

    @Operation(summary = "Edit a device (full replacement)",
            description = "Omitted location is cleared, omitted status is kept; INACTIVE refuses its key. Key unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Serial taken (ATT_028)"),
            @ApiResponse(responseCode = "404", description = "No such device in your company (ATT_023)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<DeviceDto> update(@PathVariable Long id, @Valid @RequestBody DeviceForm form) {
        return ApiMessageDto.ok(service.updateDevice(id, form));
    }

    @Operation(summary = "Issue a new key; the old one stops working at once", description = "The new key is shown once.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such device in your company (ATT_023)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @PostMapping("/{id}/rotate-key")
    public ApiMessageDto<DeviceWithKey> rotateKey(@PathVariable Long id) {
        return ApiMessageDto.ok(service.rotateKey(id));
    }

    @Operation(summary = "Remove a device", description = "Its key stops working; the punches it sent stay.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such device in your company (ATT_023)"))
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.deleteDevice(id);
        return ApiMessageDto.ok(null);
    }
}
