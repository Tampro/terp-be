package com.terp.erp.project.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.project.portal.ProjectPortalService.PortalMilestone;
import com.terp.erp.project.portal.ProjectPortalService.PortalProject;
import com.terp.erp.project.portal.ProjectPortalService.PortalProjectDetail;
import com.terp.erp.project.portal.ProjectPortalService.PortalTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Client portal (read-only): projects of the customer linked to my account; no party id is taken in. */
@Tag(name = "Client portal — projects")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portal/client/projects")
@SecurityRequirement(name = "bearerAuth")
public class ClientPortalController {
    static final String NOT_LINKED = "Account not linked to a customer (ACC_098 relayed from accounting, or PRJ_026)";
    static final String NOT_MINE = "Not a project of my customer (PRJ_006); " + NOT_LINKED;
    static final String UNAVAILABLE = "Accounting service unavailable (PRJ_004)";
    private final ProjectPortalService service;

    @Operation(summary = "My projects", description = "Projects with my customer among their clients.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_LINKED),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PortalProject>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(pageable)));
    }

    @Operation(summary = "One of my projects", description = "With progress (percent of tasks in the done stage).")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_MINE),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/{id}")
    public ApiMessageDto<PortalProjectDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Milestones of one of my projects", description = "Without cost and summary.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_MINE),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/{id}/milestones")
    public ApiMessageDto<List<PortalMilestone>> milestones(@PathVariable Long id) {
        return ApiMessageDto.ok(service.milestones(id));
    }

    @Operation(summary = "Tasks of one of my projects", description = "Title, priority, stage and dates only.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_MINE),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/{id}/tasks")
    public ApiMessageDto<List<PortalTask>> tasks(@PathVariable Long id) {
        return ApiMessageDto.ok(service.tasks(id));
    }
}
