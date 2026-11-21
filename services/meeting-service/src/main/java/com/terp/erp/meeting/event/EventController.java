package com.terp.erp.meeting.event;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.event.CompanyEventService.EventDto;
import com.terp.erp.meeting.event.CompanyEventService.EventForm;
import com.terp.erp.meeting.event.CompanyEventService.Filter;
import com.terp.erp.meeting.event.CompanyEventService.StatusCounts;
import com.terp.erp.meeting.event.EventTypeService.Option;
import com.terp.erp.meeting.event.EventTypeService.TypeDto;
import com.terp.erp.meeting.event.EventTypeService.TypeForm;

import java.time.LocalDate;
import java.util.List;

import static com.terp.erp.meeting.common.Texts.COMPANY;

/** Events (docs/analysis/06 §9.2) and their types; under /api/v1/meeting, the route of meeting-service. */
@Tag(name = "Events")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting")
@SecurityRequirement(name = "bearerAuth")
public class EventController {
    private static final String NO_COMPANY = "Super admin without companyId (MTG_001)";
    private static final String TYPE_NOT_FOUND = "No such event type in your company (MTG_016)";
    private static final String NOT_FOUND = "No such event in your company (MTG_017)";
    private static final String FORM_ERRORS = "Ends before it starts (MTG_018), type not in the company (MTG_016), "
            + "inactive type (MTG_008), department not an active one of the company (MTG_020)";

    private final EventTypeService types;
    private final CompanyEventService events;

    public record StatusForm(@NotNull EStatus status) {
    }

    // ------------------------------------------------------------------ event types

    @Operation(summary = "Event types of the company", description = "keyword = name; status filter; sortable by name.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/event-types")
    public ApiMessageDto<ResponseListDto<TypeDto>> types(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(types.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Event type dropdown", description = "Active types only.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/event-types/options")
    public ApiMessageDto<List<Option>> typeOptions(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(types.options(companyId));
    }

    @Operation(summary = "Event type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/event-types/{id}")
    public ApiMessageDto<TypeDto> type(@PathVariable Long id) {
        return ApiMessageDto.ok(types.get(id));
    }

    @Operation(summary = "Add Event Type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (MTG_003), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @PostMapping("/event-types")
    public ApiMessageDto<TypeDto> createType(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(types.create(companyId, form));
    }

    @Operation(summary = "Edit an event type", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (MTG_003)"),
            @ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @PutMapping("/event-types/{id}")
    public ApiMessageDto<TypeDto> updateType(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(types.update(id, form));
    }

    @Operation(summary = "Toggle Status of an event type", description = "An inactive type can no longer be chosen "
            + "for new or edited events.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @PutMapping("/event-types/{id}/status")
    public ApiMessageDto<TypeDto> typeStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(types.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an event type", description = "Only a type without events.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The type has events (MTG_004)"),
            @ApiResponse(responseCode = "404", description = TYPE_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @DeleteMapping("/event-types/{id}")
    public ApiMessageDto<Void> deleteType(@PathVariable Long id) {
        types.delete(id);
        return ApiMessageDto.ok(null);
    }

    // ------------------------------------------------------------------ events

    @Operation(summary = "Events of the company", description = "keyword = title, location or description; filters: "
            + "eventTypeId, departmentId, status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/events")
    public ApiMessageDto<ResponseListDto<EventDto>> events(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long eventTypeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) CompanyEvent.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(events.search(companyId,
                new Filter(keyword, eventTypeId, departmentId), status, pageable)));
    }

    @Operation(summary = "Counts per status (All, Pending, Approved, Rejected)", description = "Same filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/events/status-counts")
    public ApiMessageDto<StatusCounts> eventCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long eventTypeId,
            @RequestParam(required = false) Long departmentId) {
        return ApiMessageDto.ok(events.statusCounts(companyId, new Filter(keyword, eventTypeId, departmentId)));
    }

    @Operation(summary = "Calendar: approved events overlapping a period", description = "At most 92 days.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or too long (MTG_021), "
            + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/events/calendar")
    public ApiMessageDto<List<EventDto>> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(events.calendar(companyId, from, to));
    }

    @Operation(summary = "Event details (View popup)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('EVENT_VIEW')")
    @GetMapping("/events/{id}")
    public ApiMessageDto<EventDto> event(@PathVariable Long id) {
        return ApiMessageDto.ok(events.get(id));
    }

    @Operation(summary = "Create an event", description = "Created PENDING. Departments are checked in "
            + "employee-service as you (active departments of the company).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (MTG_006)")})
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @PostMapping("/events")
    public ApiMessageDto<EventDto> createEvent(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody EventForm form) {
        return ApiMessageDto.ok(events.create(companyId, form));
    }

    @Operation(summary = "Edit an event", description = "Full replacement; an approved or rejected event goes back "
            + "to PENDING (PO, 2026-11-21). A department or inactive type already on the event may stay.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @PutMapping("/events/{id}")
    public ApiMessageDto<EventDto> updateEvent(@PathVariable Long id, @Valid @RequestBody EventForm form) {
        return ApiMessageDto.ok(events.update(id, form));
    }

    @Operation(summary = "Approve a pending event")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (MTG_019)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EVENT_APPROVE')")
    @PutMapping("/events/{id}/approve")
    public ApiMessageDto<EventDto> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(events.decide(id, true));
    }

    @Operation(summary = "Reject a pending event")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (MTG_019)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('EVENT_APPROVE')")
    @PutMapping("/events/{id}/reject")
    public ApiMessageDto<EventDto> reject(@PathVariable Long id) {
        return ApiMessageDto.ok(events.decide(id, false));
    }

    @Operation(summary = "Delete an event")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('EVENT_MANAGE')")
    @DeleteMapping("/events/{id}")
    public ApiMessageDto<Void> deleteEvent(@PathVariable Long id) {
        events.delete(id);
        return ApiMessageDto.ok(null);
    }
}
