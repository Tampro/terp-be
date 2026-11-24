package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.goal.GoalService.ContributionDto;
import com.terp.erp.accounting.goal.GoalService.ContributionForm;
import com.terp.erp.accounting.goal.GoalService.GoalDto;
import com.terp.erp.accounting.goal.GoalService.GoalForm;
import com.terp.erp.accounting.goal.GoalService.MilestoneDto;
import com.terp.erp.accounting.goal.GoalService.MilestoneForm;
import com.terp.erp.accounting.goal.GoalService.TrackingDto;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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

import java.util.List;

@Tag(name = "Financial goals")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/goals")
@SecurityRequirement(name = "bearerAuth")
public class GoalController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such goal in your company (ACC_080)";
    private static final String INVALID = "Target date not after start (ACC_081), account inactive or not in the "
            + "company (ACC_020), validation (COMMON)";

    private final GoalService service;

    @Operation(summary = "Financial goals", description = "Filters categoryId, status, type.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<GoalDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) EGoalStatus status,
            @RequestParam(required = false) EGoalType type,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, categoryId, status, type, pageable)));
    }

    @Operation(summary = "Count per status", description = "Same filters as the list (categoryId, type), without "
            + "status; every status is present, 0 when none. All = the sum.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<java.util.Map<EGoalStatus, Long>> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) EGoalType type) {
        return ApiMessageDto.ok(service.statusCounts(companyId, categoryId, type));
    }

    @Operation(summary = "Goal details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<GoalDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a goal", description = "Starts DRAFT; priority null = MEDIUM.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId (ACC_001)"),
            @ApiResponse(responseCode = "404", description = "Category not in your company (ACC_078)")})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping
    public ApiMessageDto<GoalDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody GoalForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a draft goal", description = "Full replacement; DRAFT only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = INVALID + ", not DRAFT (ACC_082)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + "; category (ACC_078)")})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<GoalDto> update(@PathVariable Long id, @Valid @RequestBody GoalForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a goal", description = "Soft delete, any status.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Activate a goal", description = "DRAFT → ACTIVE.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not DRAFT (ACC_082)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping("/{id}/activate")
    public ApiMessageDto<GoalDto> activate(@PathVariable Long id) {
        return ApiMessageDto.ok(service.activate(id));
    }

    @Operation(summary = "Complete a goal", description = "ACTIVE → COMPLETED whatever the progress. A manual "
            + "contribution that brings the current amount to the target completes the goal by itself.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not ACTIVE (ACC_083)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping("/{id}/complete")
    public ApiMessageDto<GoalDto> complete(@PathVariable Long id) {
        return ApiMessageDto.ok(service.complete(id));
    }

    @Operation(summary = "Goal tracking", description = "Current amount (manual + automatic), previous amount "
            + "(before the last manual contribution), progress %, elapsed-time %, days remaining, linear projected "
            + "completion date, AHEAD/ON_TRACK/BEHIND/CRITICAL, milestones.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/{id}/tracking")
    public ApiMessageDto<TrackingDto> tracking(@PathVariable Long id) {
        return ApiMessageDto.ok(service.tracking(id));
    }

    @Operation(summary = "Goal milestones", description = "Status ACHIEVED/PENDING and overdue computed on read.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/{id}/milestones")
    public ApiMessageDto<List<MilestoneDto>> milestones(@PathVariable Long id) {
        return ApiMessageDto.ok(service.milestones(id));
    }

    @Operation(summary = "Add a milestone")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping("/{id}/milestones")
    public ApiMessageDto<MilestoneDto> addMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneForm form) {
        return ApiMessageDto.ok(service.addMilestone(id, form));
    }

    @Operation(summary = "Edit a milestone", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", milestone not on the goal (ACC_084)")})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PutMapping("/{id}/milestones/{milestoneId}")
    public ApiMessageDto<MilestoneDto> updateMilestone(@PathVariable Long id, @PathVariable Long milestoneId,
                                                       @Valid @RequestBody MilestoneForm form) {
        return ApiMessageDto.ok(service.updateMilestone(id, milestoneId, form));
    }

    @Operation(summary = "Delete a milestone")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + ", milestone not on the goal "
            + "(ACC_084)"))
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @DeleteMapping("/{id}/milestones/{milestoneId}")
    public ApiMessageDto<Void> deleteMilestone(@PathVariable Long id, @PathVariable Long milestoneId) {
        service.deleteMilestone(id, milestoneId);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Goal contributions", description = "MANUAL rows newest first, then one computed "
            + "AUTOMATIC row (id null) when the goal has a chart account.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('GOAL_VIEW')")
    @GetMapping("/{id}/contributions")
    public ApiMessageDto<List<ContributionDto>> contributions(@PathVariable Long id) {
        return ApiMessageDto.ok(service.contributions(id));
    }

    @Operation(summary = "Add a manual contribution", description = "ACTIVE goals only; reaching the target "
            + "completes the goal.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not ACTIVE (ACC_083), validation (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @PostMapping("/{id}/contributions")
    public ApiMessageDto<ContributionDto> addContribution(@PathVariable Long id,
                                                          @Valid @RequestBody ContributionForm form) {
        return ApiMessageDto.ok(service.addContribution(id, form));
    }

    @Operation(summary = "Delete a manual contribution", description = "ACTIVE goals only.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not ACTIVE (ACC_083)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", contribution not on the goal "
                    + "(ACC_085)")})
    @PreAuthorize("@PermissionChecker.has('GOAL_MANAGE')")
    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ApiMessageDto<Void> deleteContribution(@PathVariable Long id, @PathVariable Long contributionId) {
        service.deleteContribution(id, contributionId);
        return ApiMessageDto.ok(null);
    }
}
