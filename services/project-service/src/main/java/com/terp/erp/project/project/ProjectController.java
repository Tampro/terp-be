package com.terp.erp.project.project;

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
import com.terp.erp.project.project.ActivityService.ActivityDto;
import com.terp.erp.project.project.ProjectDuplicateService.DuplicateForm;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectPartsService.AttachmentDto;
import com.terp.erp.project.project.ProjectPartsService.AttachmentForm;
import com.terp.erp.project.project.ProjectPartsService.MilestoneDto;
import com.terp.erp.project.project.ProjectPartsService.MilestoneForm;
import com.terp.erp.project.project.ProjectReportService.ProjectReport;
import com.terp.erp.project.project.ProjectService.ClientsForm;
import com.terp.erp.project.project.ProjectService.ProjectDetailDto;
import com.terp.erp.project.project.ProjectService.ProjectDto;
import com.terp.erp.project.project.ProjectService.ProjectForm;
import com.terp.erp.project.project.ProjectService.StatusForm;

import java.util.List;

@Tag(name = "Projects")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {
    private static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    private static final String VIEW = "@PermissionChecker.has('PROJECT_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('PROJECT_MANAGE')";
    private static final String NOT_FOUND = "Project not found or of another company (PRJ_006)";

    private final ProjectService service;
    private final ProjectPartsService parts;
    private final ProjectDuplicateService duplicates;
    private final ProjectReportService reports;
    private final ActivityService activity;

    @Operation(summary = "Projects of the company (filter by status, name)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PRJ_001)"))
    @PreAuthorize(VIEW)
    @GetMapping
    public ApiMessageDto<ResponseListDto<ProjectDto>> list(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                           @RequestParam(required = false) EProjectStatus status,
                                                           @Parameter(description = "Part of the name") @RequestParam(required = false) String q,
                                                           @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, status, q, pageable)));
    }

    @Operation(summary = "Create a project (status ONGOING); team members are checked through IAM")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "PRJ_001, end before start (PRJ_007), "
            + "user not of the company (PRJ_008), validation (COMMON_001)"),
            @ApiResponse(responseCode = "403", description = "Caller cannot read IAM users (COMMON_004)"),
            @ApiResponse(responseCode = "503", description = "IAM down (PRJ_005)")})
    @PreAuthorize(MANAGE)
    @PostMapping
    public ApiMessageDto<ProjectDto> create(@Valid @RequestBody ProjectForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Project detail: team, deadline, budget, days left, progress")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}")
    public ApiMessageDto<ProjectDetailDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.detail(id));
    }

    @Operation(summary = "Update a project", description = "Full replacement of the fields and of the team "
            + "(userIds); companyId is ignored. Removed members are taken off the project's tasks.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "PRJ_007, PRJ_008, COMMON_001"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "PRJ_005")})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public ApiMessageDto<ProjectDto> update(@PathVariable Long id, @Valid @RequestBody ProjectForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change the status (ONGOING, ON_HOLD, FINISHED)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/status")
    public ApiMessageDto<ProjectDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @Operation(summary = "Replace the clients (accounting customers)", description = "Full replacement of the list.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Customer not of the company (PRJ_009)"),
            @ApiResponse(responseCode = "403", description = "Caller cannot read customers (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "Accounting down (PRJ_004)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/clients")
    public ApiMessageDto<ProjectDto> clients(@PathVariable Long id, @Valid @RequestBody ClientsForm form) {
        return ApiMessageDto.ok(service.replaceClients(id, form));
    }

    @Operation(summary = "Delete a project (soft)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Duplicate a project with the chosen parts")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/duplicate")
    public ApiMessageDto<ProjectDto> duplicate(@PathVariable Long id, @Valid @RequestBody DuplicateForm form) {
        return ApiMessageDto.ok(duplicates.duplicate(id, form));
    }

    @Operation(summary = "Project report: status, budget, timeline, tasks by stage/priority, per user, milestones")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}/report")
    public ApiMessageDto<ProjectReport> report(@PathVariable Long id) {
        return ApiMessageDto.ok(reports.report(id));
    }

    @Operation(summary = "Activity log, newest first")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}/activity")
    public ApiMessageDto<ResponseListDto<ActivityDto>> activity(@PathVariable Long id, @ParameterObject Pageable pageable) {
        service.require(id);
        return ApiMessageDto.ok(ResponseListDto.of(activity.list(id, pageable)));
    }

    // ---------- milestones ----------

    @Operation(summary = "Milestones of a project")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}/milestones")
    public ApiMessageDto<List<MilestoneDto>> milestones(@PathVariable Long id) {
        return ApiMessageDto.ok(parts.milestones(id));
    }

    @Operation(summary = "Add a milestone")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "End before start (PRJ_007), COMMON_001"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/milestones")
    public ApiMessageDto<MilestoneDto> addMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneForm form) {
        return ApiMessageDto.ok(parts.addMilestone(id, form));
    }

    @Operation(summary = "Update a milestone", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "PRJ_007, COMMON_001"),
            @ApiResponse(responseCode = "404", description = "PRJ_010")})
    @PreAuthorize(MANAGE)
    @PutMapping("/milestones/{milestoneId}")
    public ApiMessageDto<MilestoneDto> updateMilestone(@PathVariable Long milestoneId, @Valid @RequestBody MilestoneForm form) {
        return ApiMessageDto.ok(parts.updateMilestone(milestoneId, form));
    }

    @Operation(summary = "Delete a milestone (its tasks lose the link)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_010"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/milestones/{milestoneId}")
    public ApiMessageDto<Void> deleteMilestone(@PathVariable Long milestoneId) {
        parts.deleteMilestone(milestoneId);
        return ApiMessageDto.ok(null);
    }

    // ---------- attachments ----------

    @Operation(summary = "Attachments of a project")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}/attachments")
    public ApiMessageDto<List<AttachmentDto>> attachments(@PathVariable Long id) {
        return ApiMessageDto.ok(parts.attachments(id));
    }

    @Operation(summary = "Attach a media file of the company")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a file of the company (PRJ_021)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "Media down (PRJ_002)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/attachments")
    public ApiMessageDto<AttachmentDto> attach(@PathVariable Long id, @Valid @RequestBody AttachmentForm form) {
        return ApiMessageDto.ok(parts.attach(id, form));
    }

    @Operation(summary = "Detach a file (the media file stays)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_022"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/attachments/{attachmentId}")
    public ApiMessageDto<Void> detach(@PathVariable Long attachmentId) {
        parts.detach(attachmentId);
        return ApiMessageDto.ok(null);
    }
}
