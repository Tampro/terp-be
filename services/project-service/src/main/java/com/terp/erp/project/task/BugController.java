package com.terp.erp.project.task;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.project.task.BugService.BugColumn;
import com.terp.erp.project.task.BugService.BugDto;
import com.terp.erp.project.task.BugService.BugForm;
import com.terp.erp.project.task.TaskService.MoveForm;

import java.util.List;

@Tag(name = "Projects — bugs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class BugController {
    private static final String VIEW = "@PermissionChecker.hasAny('PROJECT_VIEW','PROJECT_VIEW_OWN')";
    private static final String MANAGE = "@PermissionChecker.hasAny('PROJECT_TASK_MANAGE','PROJECT_TASK_MANAGE_OWN')";
    private static final String NOT_FOUND = "Bug (PRJ_020) not found or of another company";
    private final BugService service;

    @Operation(summary = "Bug Kanban: the bug stages in order, each with its cards")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_006"))
    @PreAuthorize(VIEW)
    @GetMapping("/{projectId}/bugs/board")
    public ApiMessageDto<List<BugColumn>> board(@PathVariable Long projectId) {
        return ApiMessageDto.ok(service.board(projectId));
    }

    @Operation(summary = "Report a bug (default stage: the first one)")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Assignee not on the team (PRJ_017), "
            + "stage of another board (PRJ_024), COMMON_001"),
            @ApiResponse(responseCode = "404", description = "PRJ_006, stage PRJ_012")})
    @PreAuthorize(MANAGE)
    @PostMapping("/{projectId}/bugs")
    public ApiMessageDto<BugDto> create(@PathVariable Long projectId, @Valid @RequestBody BugForm form) {
        return ApiMessageDto.ok(service.create(projectId, form));
    }

    @Operation(summary = "A bug")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/bugs/{bugId}")
    public ApiMessageDto<BugDto> get(@PathVariable Long bugId) {
        return ApiMessageDto.ok(service.get(bugId));
    }

    @Operation(summary = "Update a bug", description = "Full replacement of the fields; stageId is ignored (use move).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "PRJ_017, COMMON_001"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/bugs/{bugId}")
    public ApiMessageDto<BugDto> update(@PathVariable Long bugId, @Valid @RequestBody BugForm form) {
        return ApiMessageDto.ok(service.update(bugId, form));
    }

    @Operation(summary = "Move a bug to a stage (status) and position")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Stage of the task board (PRJ_024)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", stage PRJ_012")})
    @PreAuthorize(MANAGE)
    @PutMapping("/bugs/{bugId}/move")
    public ApiMessageDto<BugDto> move(@PathVariable Long bugId, @Valid @RequestBody MoveForm form) {
        return ApiMessageDto.ok(service.move(bugId, form));
    }

    @Operation(summary = "Delete a bug (soft)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/bugs/{bugId}")
    public ApiMessageDto<Void> delete(@PathVariable Long bugId) {
        service.delete(bugId);
        return ApiMessageDto.ok(null);
    }
}
