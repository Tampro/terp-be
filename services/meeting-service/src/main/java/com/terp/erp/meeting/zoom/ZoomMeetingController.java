package com.terp.erp.meeting.zoom;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.meeting.zoom.ZoomMeeting.EStatus;
import com.terp.erp.meeting.zoom.ZoomMeetingService.CalendarEntry;
import com.terp.erp.meeting.zoom.ZoomMeetingService.MeetingDto;
import com.terp.erp.meeting.zoom.ZoomMeetingService.MeetingForm;
import com.terp.erp.meeting.zoom.ZoomMeetingService.StartDto;

import java.time.LocalDate;
import java.util.List;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Zoom meetings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/zoom/meetings")
@SecurityRequirement(name = "bearerAuth")
public class ZoomMeetingController {
    private static final String NOT_FOUND = "No such Zoom meeting in your company (MTG_027)";
    private static final String ZOOM_ERRORS = "Zoom not set up / not tested (MTG_022), invitee not an employee "
            + "(MTG_013)";
    private static final String DOWN = "Zoom unavailable (MTG_024); nothing was changed";

    private final ZoomMeetingService service;

    public record StatusForm(@NotNull EStatus status) {
    }

    @Operation(summary = "Zoom meetings starting in a period", description = "For the month / week / day views and "
            + "the selected date panel (from = to); at most 42 days. passcode and startUrl only for ZOOM_MANAGE. "
            + "Needs EMPLOYEE_VIEW too (invitee photos).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or too long (MTG_021), super "
            + "admin without companyId (MTG_001)"))
    @PreAuthorize("@PermissionChecker.has('ZOOM_VIEW')")
    @GetMapping
    public ApiMessageDto<List<MeetingDto>> between(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EStatus status) {
        return ApiMessageDto.ok(service.between(companyId, from, to, status));
    }

    @Operation(summary = "Unified calendar: Zoom meetings starting in a period", description = "At most 62 days; "
            + "cancelled ones included (see status). No invitees or join data, so EMPLOYEE_VIEW is not needed.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or too long (MTG_021), super "
            + "admin without companyId (MTG_001)"))
    @PreAuthorize("@PermissionChecker.has('ZOOM_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<List<CalendarEntry>> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to));
    }

    @Operation(summary = "Meeting Details (View)", description = "Meeting ID, join URL; passcode and start URL for "
            + "ZOOM_MANAGE.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ZOOM_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<MeetingDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Schedule Zoom Meeting", description = "Created on Zoom first (Asia/Ho_Chi_Minh time); "
            + "passcode omitted = a random 6-digit one.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ZOOM_ERRORS + ", super admin without companyId "
                    + "(MTG_001)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PostMapping
    public ApiMessageDto<MeetingDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody MeetingForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a Zoom meeting", description = "Full replacement, on Zoom too; passcode omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ZOOM_ERRORS + ", ended or cancelled (MTG_028)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<MeetingDto> update(@PathVariable Long id, @Valid @RequestBody MeetingForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change Status", description = "SCHEDULED, STARTED, ENDED or CANCELLED (not sent to Zoom).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<MeetingDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Start Meeting", description = "Marks it STARTED and returns the host start URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Ended or cancelled (MTG_028)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @PostMapping("/{id}/start")
    public ApiMessageDto<StartDto> start(@PathVariable Long id) {
        return ApiMessageDto.ok(service.start(id));
    }

    @Operation(summary = "Delete a Zoom meeting", description = "Removed from Zoom Cloud too; a meeting Zoom no "
            + "longer has is still deleted here.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Zoom not set up / not tested (MTG_022)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PreAuthorize("@PermissionChecker.has('ZOOM_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
