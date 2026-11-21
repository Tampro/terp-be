package com.terp.erp.meeting.zoom;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.meeting.zoom.ZoomSettingService.SettingsDto;
import com.terp.erp.meeting.zoom.ZoomSettingService.SettingsForm;
import com.terp.erp.meeting.zoom.ZoomSettingService.Status;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Zoom settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/zoom")
@SecurityRequirement(name = "bearerAuth")
public class ZoomSettingController {
    private static final String NO_COMPANY = "Super admin without companyId (MTG_001)";

    private final ZoomSettingService service;

    @Operation(summary = "Is the Zoom Meeting menu enabled?", description = "enabled = credentials saved and the last "
            + "Test Connection succeeded.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('ZOOM_VIEW')")
    @GetMapping("/status")
    public ApiMessageDto<Status> status(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.status(companyId));
    }

    @Operation(summary = "Zoom Settings", description = "The client secret is never returned (clientSecretSet).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @GetMapping("/settings")
    public ApiMessageDto<SettingsDto> get(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Save Zoom Settings", description = "Server-to-Server OAuth Account ID, Client ID and Client "
            + "Secret (encrypted; blank = keep the stored one). Saving requires a new Test Connection.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Secret missing the first time (MTG_025), "
            + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PutMapping("/settings")
    public ApiMessageDto<SettingsDto> save(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }

    @Operation(summary = "Test Connection", description = "Asks Zoom for an access token with the stored credentials.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not saved yet (MTG_022), Zoom rejected the credentials "
                    + "(MTG_023), stored secret unreadable (MTG_026)"),
            @ApiResponse(responseCode = "503", description = "Zoom unavailable (MTG_024)")})
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PostMapping("/settings/test")
    public ApiMessageDto<SettingsDto> test(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.test(companyId));
    }
}
