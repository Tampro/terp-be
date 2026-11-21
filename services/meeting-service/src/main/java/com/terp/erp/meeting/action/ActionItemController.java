package com.terp.erp.meeting.action;

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
import com.terp.erp.meeting.action.ActionItem.EPriority;
import com.terp.erp.meeting.action.ActionItemService.ETab;
import com.terp.erp.meeting.action.ActionItemService.Filter;
import com.terp.erp.meeting.action.ActionItemService.ItemDto;
import com.terp.erp.meeting.action.ActionItemService.ItemForm;
import com.terp.erp.meeting.action.ActionItemService.ProgressForm;
import com.terp.erp.meeting.action.ActionItemService.TabCounts;

import static com.terp.erp.meeting.common.Texts.COMPANY;

@Tag(name = "Action items")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meeting/action-items")
@SecurityRequirement(name = "bearerAuth")
public class ActionItemController {
    private static final String KEYWORD = "Matches the title, description or assignee name";
    private static final String NOT_FOUND = "No such action item in your company (MTG_015)";
    private static final String NO_COMPANY = "Super admin without companyId (MTG_001)";
    private static final String FORM_ERRORS = "Meeting not in the company (MTG_014), assignee not an employee of it "
            + "(MTG_013)";

    private final ActionItemService service;

    @Operation(summary = "Action items of the company", description = "Filters: priority, assigneeId, meetingId; tab "
            + "omitted = All. OVERDUE = not completed and due before today (Vietnam date); NOT_STARTED and "
            + "IN_PROGRESS leave overdue items out. Needs EMPLOYEE_VIEW too (photos).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (MTG_006)")})
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ItemDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) ETab tab,
            @ParameterObject @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId,
                new Filter(keyword, priority, assigneeId, meetingId), tab, pageable)));
    }

    @Operation(summary = "Counts of the tabs (All, Not Started, In Progress, Completed, Overdue)",
            description = "Same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/tab-counts")
    public ApiMessageDto<TabCounts> tabCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long meetingId) {
        return ApiMessageDto.ok(service.tabCounts(companyId, new Filter(keyword, priority, assigneeId, meetingId)));
    }

    @Operation(summary = "Action item details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ItemDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Action Item", description = "Starts at 0 % (Not Started). The assignee is checked in "
            + "employee-service as you.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (MTG_006)")})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PostMapping
    public ApiMessageDto<ItemDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody ItemForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an action item", description = "Full replacement of the details; priority omitted = "
            + "kept; progress unchanged (Update Progress).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ItemDto> update(@PathVariable Long id, @Valid @RequestBody ItemForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Progress", description = "0–100 %; 0 = Not Started, 1–99 = In Progress, 100 = "
            + "Completed.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @PutMapping("/{id}/progress")
    public ApiMessageDto<ItemDto> progress(@PathVariable Long id, @Valid @RequestBody ProgressForm form) {
        return ApiMessageDto.ok(service.progress(id, form));
    }

    @Operation(summary = "Delete an action item")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('MEETING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
