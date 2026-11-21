package com.terp.erp.meeting.meeting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import com.terp.erp.meeting.meeting.MeetingService.Calendar;
import com.terp.erp.meeting.meeting.MeetingService.CreateResult;
import com.terp.erp.meeting.meeting.MeetingService.MeetingDto;
import com.terp.erp.meeting.meeting.MeetingService.MeetingForm;
import com.terp.erp.meeting.meeting.MeetingService.Summary;

import java.time.LocalDate;
import java.util.List;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Meetings")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/meetings")
@SecurityRequirement(name = "bearerAuth")
public class MeetingController {
    private static final String NOT_FOUND = "No such meeting in your company (MTG_014)";
    private static final String NO_COMPANY = "Super admin without companyId (MTG_001)";
    private static final String DATE = "Omitted = today (Vietnam date). Quick filters: send yesterday / tomorrow.";
    private static final String FORM_ERRORS = "End not after start (MTG_007), type (MTG_002) or room (MTG_005) not in "
            + "the company, inactive type / room (MTG_008), room busy (MTG_011), over capacity (MTG_012), organizer or "
            + "attendee not an employee (MTG_013)";

    private final MeetingService service;

    public record StatusForm(@NotNull Meeting.EStatus status) {
    }

    @Operation(summary = "Meetings of a date", description = "By start time, with organizer, type, room, the first 5 "
            + "attendees (attendeeCount = all) and duration. " + DATE + " Needs EMPLOYEE_VIEW too (photos).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (MTG_006)")})
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping
    public ApiMessageDto<List<MeetingDto>> ofDay(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = DATE) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Meeting.EStatus status) {
        return ApiMessageDto.ok(service.ofDay(companyId, date, status));
    }

    @Operation(summary = "Meetings of a period (company calendar)",
            description = "Meetings with meetingDate in [from, to] (at most 62 days), by date and start time; "
                    + "cancelled ones included (see status).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or longer than 62 days "
            + "(MTG_021), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/range")
    public ApiMessageDto<List<MeetingDto>> range(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.inRange(companyId, from, to));
    }

    @Operation(summary = "Meeting Summary of a date", description = "Counts per status and the total. " + DATE)
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/summary")
    public ApiMessageDto<Summary> summary(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = DATE) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiMessageDto.ok(service.summary(companyId, date));
    }

    @Operation(summary = "Mini calendar of a month", description = "The dates with meetings (the dots) and today.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<Calendar> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return ApiMessageDto.ok(service.calendar(companyId, year, month));
    }

    @Operation(summary = "Meeting details", description = "With every attendee.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<MeetingDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Schedule New Meeting", description = "A recurrence (DAILY, WEEKLY, MONTHLY) generates one "
            + "meeting per occurrence up to the recurrence end date (at most 100), sharing seriesId; each is then "
            + "edited, updated and deleted on its own. Organizer and attendees are checked in employee-service as "
            + "you.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", recurrence end missing or before the "
                    + "date (MTG_009), more than 100 occurrences (MTG_010), " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (MTG_006)")})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PostMapping
    public ApiMessageDto<CreateResult> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody MeetingForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a meeting", description = "Full replacement of this occurrence only; recurrence and "
            + "recurrence end date are ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<MeetingDto> update(@PathVariable Long id, @Valid @RequestBody MeetingForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status", description = "SCHEDULED, IN_PROGRESS, COMPLETED (shown as Held) or "
            + "CANCELLED. Reviving a cancelled meeting checks its physical room again.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Room busy (MTG_011)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<MeetingDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a meeting", description = "This occurrence only.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
