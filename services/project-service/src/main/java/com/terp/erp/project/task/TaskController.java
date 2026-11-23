package com.terp.erp.project.task;

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
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.task.TaskService.CommentDto;
import com.terp.erp.project.task.TaskService.CommentForm;
import com.terp.erp.project.task.TaskService.MoveForm;
import com.terp.erp.project.task.TaskService.SubtaskDto;
import com.terp.erp.project.task.TaskService.SubtaskForm;
import com.terp.erp.project.task.TaskService.TaskCalendarItem;
import com.terp.erp.project.task.TaskService.TaskColumn;
import com.terp.erp.project.task.TaskService.TaskDetailDto;
import com.terp.erp.project.task.TaskService.TaskDto;
import com.terp.erp.project.task.TaskService.TaskForm;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Projects — tasks")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {
    private static final String VIEW = "@PermissionChecker.hasAny('PROJECT_VIEW','PROJECT_VIEW_OWN')";
    private static final String MANAGE = "@PermissionChecker.hasAny('PROJECT_TASK_MANAGE','PROJECT_TASK_MANAGE_OWN')";
    private static final String NOT_FOUND = "Task (PRJ_016) or project (PRJ_006) not found or of another company";
    private final TaskService service;

    @Operation(summary = "Task Kanban: the task stages in order, each with its cards")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_006"))
    @PreAuthorize(VIEW)
    @GetMapping("/{projectId}/tasks/board")
    public ApiMessageDto<List<TaskColumn>> board(@PathVariable Long projectId) {
        return ApiMessageDto.ok(service.board(projectId));
    }

    @Operation(summary = "Task list with filters (priority, due date range, assignee)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_006"))
    @PreAuthorize(VIEW)
    @GetMapping("/{projectId}/tasks")
    public ApiMessageDto<ResponseListDto<TaskDto>> list(@PathVariable Long projectId,
                                                        @RequestParam(required = false) EPriority priority,
                                                        @Parameter(description = "Due on or after") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @Parameter(description = "Due on or before") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @Parameter(description = "IAM user id") @RequestParam(required = false) Long assigneeId,
                                                        @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(projectId, priority, from, to, assigneeId, pageable)));
    }

    @Operation(summary = "Tasks due in [from, to] across the caller's company (unified calendar)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Bad range or over 366 days (PRJ_023), PRJ_001"))
    // Company-wide: not opened to PROJECT_VIEW_OWN (it would list tasks of projects the caller cannot see).
    @PreAuthorize("@PermissionChecker.has('PROJECT_VIEW')")
    @GetMapping("/tasks/calendar")
    public ApiMessageDto<List<TaskCalendarItem>> calendar(
            @Parameter(description = "Super admin only: the company") @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to));
    }

    @Operation(summary = "Create a task (default stage: the first one)")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Milestone of another project (PRJ_011), "
            + "assignee not on the team (PRJ_017), stage of another board (PRJ_024), due before start (PRJ_025), COMMON_001"),
            @ApiResponse(responseCode = "404", description = "PRJ_006, stage PRJ_012")})
    @PreAuthorize(MANAGE)
    @PostMapping("/{projectId}/tasks")
    public ApiMessageDto<TaskDto> create(@PathVariable Long projectId, @Valid @RequestBody TaskForm form) {
        return ApiMessageDto.ok(service.create(projectId, form));
    }

    @Operation(summary = "Task with subtasks and comments")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/tasks/{taskId}")
    public ApiMessageDto<TaskDetailDto> get(@PathVariable Long taskId) {
        return ApiMessageDto.ok(service.detail(taskId));
    }

    @Operation(summary = "Update a task", description = "Full replacement of the fields; stageId is ignored (use move).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "PRJ_011, PRJ_017, PRJ_025, COMMON_001"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/tasks/{taskId}")
    public ApiMessageDto<TaskDto> update(@PathVariable Long taskId, @Valid @RequestBody TaskForm form) {
        return ApiMessageDto.ok(service.update(taskId, form));
    }

    @Operation(summary = "Move a card to a stage and position (last stage = done)")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Stage of the bug board (PRJ_024)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", stage PRJ_012")})
    @PreAuthorize(MANAGE)
    @PutMapping("/tasks/{taskId}/move")
    public ApiMessageDto<TaskDto> move(@PathVariable Long taskId, @Valid @RequestBody MoveForm form) {
        return ApiMessageDto.ok(service.move(taskId, form));
    }

    @Operation(summary = "Delete a task (soft)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/tasks/{taskId}")
    public ApiMessageDto<Void> delete(@PathVariable Long taskId) {
        service.delete(taskId);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Add a subtask")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @PostMapping("/tasks/{taskId}/subtasks")
    public ApiMessageDto<SubtaskDto> addSubtask(@PathVariable Long taskId, @Valid @RequestBody SubtaskForm form) {
        return ApiMessageDto.ok(service.addSubtask(taskId, form));
    }

    @Operation(summary = "Update a subtask", description = "Full replacement (name, done).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_018"))
    @PreAuthorize(MANAGE)
    @PutMapping("/subtasks/{subtaskId}")
    public ApiMessageDto<SubtaskDto> updateSubtask(@PathVariable Long subtaskId, @Valid @RequestBody SubtaskForm form) {
        return ApiMessageDto.ok(service.updateSubtask(subtaskId, form));
    }

    @Operation(summary = "Delete a subtask")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_018"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/subtasks/{subtaskId}")
    public ApiMessageDto<Void> deleteSubtask(@PathVariable Long subtaskId) {
        service.deleteSubtask(subtaskId);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Comments of a task, oldest first")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/tasks/{taskId}/comments")
    public ApiMessageDto<List<CommentDto>> comments(@PathVariable Long taskId) {
        return ApiMessageDto.ok(service.comments(taskId));
    }

    @Operation(summary = "Comment on a task")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @PostMapping("/tasks/{taskId}/comments")
    public ApiMessageDto<CommentDto> addComment(@PathVariable Long taskId, @Valid @RequestBody CommentForm form) {
        return ApiMessageDto.ok(service.addComment(taskId, form));
    }

    @Operation(summary = "Delete a comment (soft)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_019"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/comments/{commentId}")
    public ApiMessageDto<Void> deleteComment(@PathVariable Long commentId) {
        service.deleteComment(commentId);
        return ApiMessageDto.ok(null);
    }
}
