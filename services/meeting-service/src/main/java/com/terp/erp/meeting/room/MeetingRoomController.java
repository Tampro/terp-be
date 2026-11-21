package com.terp.erp.meeting.room;

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
import com.terp.erp.meeting.room.MeetingRoom.EType;
import com.terp.erp.meeting.room.MeetingRoomService.Cards;
import com.terp.erp.meeting.room.MeetingRoomService.Option;
import com.terp.erp.meeting.room.MeetingRoomService.RoomDto;
import com.terp.erp.meeting.room.MeetingRoomService.RoomForm;
import com.terp.erp.meeting.room.MeetingRoomService.StatusCounts;

import java.util.List;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Meeting rooms")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/rooms")
@SecurityRequirement(name = "bearerAuth")
public class MeetingRoomController {
    private static final String KEYWORD = "Matches the name, description or location";
    private static final String NOT_FOUND = "No such meeting room in your company (MTG_005)";
    private static final String NO_COMPANY = "Super admin without companyId (MTG_001)";

    private final MeetingRoomService service;

    public record StatusForm(@NotNull EStatus status) {
    }

    @Operation(summary = "Meeting rooms of the company", description = "type = the Type filter; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<RoomDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EType type,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, type, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EType type) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, type));
    }

    @Operation(summary = "Summary cards (Total, Active, Physical, Virtual Rooms)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/cards")
    public ApiMessageDto<Cards> cards(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.cards(companyId));
    }

    @Operation(summary = "Meeting room dropdown", description = "Active rooms by name, with type and capacity.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Meeting room details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<RoomDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Meeting Room")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (MTG_003), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PostMapping
    public ApiMessageDto<RoomDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody RoomForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a meeting room", description = "Full replacement (equipment = full list); status "
            + "omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (MTG_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<RoomDto> update(@PathVariable Long id, @Valid @RequestBody RoomForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Status toggle")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<RoomDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a meeting room", description = "Only a room without meetings; deactivate otherwise.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The room has meetings (MTG_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
