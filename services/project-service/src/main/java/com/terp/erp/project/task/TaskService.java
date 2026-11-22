package com.terp.erp.project.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ActivityService;
import com.terp.erp.project.project.Milestone;
import com.terp.erp.project.project.MilestoneRepository;
import com.terp.erp.project.project.Project;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.project.ProjectRepository;
import com.terp.erp.project.project.ProjectService;
import com.terp.erp.project.task.StageService.StageDto;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Project tasks (FR-PRJ-004): Kanban by task stage, list with filters, calendar, subtasks and comments. */
@Service
@RequiredArgsConstructor
public class TaskService {
    /** Calendar range cap: report-service asks month/week/day views, a year is plenty. */
    static final long MAX_CALENDAR_DAYS = 366;

    private final ProjectService projects;
    private final ProjectRepository projectRepo;
    private final MilestoneRepository milestones;
    private final StageService stages;
    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final TaskCommentRepository comments;
    private final ActivityService activity;

    public record TaskForm(
            @Schema(description = "Milestone of the same project") Long milestoneId,
            @Schema(description = "Title", example = "Draft the home page", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 191) String title,
            @Schema(description = "LOW, MEDIUM or HIGH (default MEDIUM)", example = "HIGH") EPriority priority,
            @Schema(description = "Task stage; null = first stage") Long stageId,
            @Schema(description = "Assignees: IAM user ids on the project team") List<@NotNull Long> assigneeIds,
            @Schema(description = "Start date", example = "2026-12-01") LocalDate startDate,
            @Schema(description = "Due date, not before the start date", example = "2026-12-05") LocalDate dueDate,
            @Schema(description = "Description") @Size(max = 20000) String description) {
    }

    public record MoveForm(
            @Schema(description = "Target stage", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long stageId,
            @Schema(description = "0-based index in the target column; null = at the end", example = "0")
            @Min(0) Integer position) {
    }

    public record SubtaskForm(
            @Schema(description = "Name", example = "Collect logos", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 191) String name,
            @Schema(description = "Done", example = "false") boolean done) {
    }

    public record CommentForm(
            @Schema(description = "Comment", example = "Waiting for the client's feedback.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 5000) String body) {
    }

    public record TaskDto(String id, String projectId, String milestoneId, String title, EPriority priority,
                          String stageId, String stageName, int position,
                          @Schema(description = "In the done (last) task stage") boolean done,
                          List<String> assigneeIds, LocalDate startDate, LocalDate dueDate,
                          @Schema(description = "Days from start to due, inclusive; null without both") Long durationDays,
                          String description, Instant createdDate) {
    }

    public record SubtaskDto(String id, String taskId, String name, boolean done) {
        static SubtaskDto of(Subtask s) {
            return new SubtaskDto(s.getId() == null ? null : s.getId().toString(), s.getTaskId().toString(),
                    s.getName(), s.isDone());
        }
    }

    public record CommentDto(String id, String taskId, String authorUserId, String authorKeycloakId, String body,
                             Instant createdDate) {
        static CommentDto of(TaskComment c) {
            return new CommentDto(c.getId() == null ? null : c.getId().toString(), c.getTaskId().toString(),
                    c.getAuthorUserId() == null ? null : c.getAuthorUserId().toString(), c.getCreatedBy(),
                    c.getBody(), c.getCreatedDate());
        }
    }

    public record TaskDetailDto(TaskDto task, List<SubtaskDto> subtasks, List<CommentDto> comments) {
    }

    public record TaskColumn(StageDto stage, List<TaskDto> tasks) {
    }

    /** One task due in the range, for the unified calendar (report-service). */
    public record TaskCalendarItem(String id, String projectId, String projectName, String title,
                                   LocalDate startDate, LocalDate dueDate, String stageName, boolean done) {
    }

    // ---------- reads ----------

    @Transactional
    public List<TaskColumn> board(Long projectId) {
        Project p = projects.require(projectId);
        List<Stage> ordered = stages.ordered(p.getCompanyId(), EStageKind.TASK);
        List<StageDto> stageDtos = StageService.dtos(ordered);
        Map<Long, List<Task>> byStage = tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(p.getId()).stream()
                .collect(Collectors.groupingBy(Task::getStageId));
        Ctx ctx = new Ctx(ordered);
        List<TaskColumn> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            out.add(new TaskColumn(stageDtos.get(i), byStage.getOrDefault(ordered.get(i).getId(), List.of()).stream()
                    .map(ctx::dto).toList()));
        }
        return out;
    }

    @Transactional
    public Page<TaskDto> list(Long projectId, EPriority priority, LocalDate from, LocalDate to, Long assigneeId,
                              Pageable pageable) {
        Project p = projects.require(projectId);
        Ctx ctx = new Ctx(stages.ordered(p.getCompanyId(), EStageKind.TASK));
        return tasks.search(p.getId(), priority, from, to, assigneeId, pageable).map(ctx::dto);
    }

    @Transactional
    public TaskDetailDto detail(Long id) {
        Task t = require(id);
        Ctx ctx = new Ctx(stages.ordered(t.getCompanyId(), EStageKind.TASK));
        return new TaskDetailDto(ctx.dto(t), subtasks.findByTaskIdOrderByIdAsc(t.getId()).stream().map(SubtaskDto::of).toList(),
                comments.findByTaskIdAndDeletedFalseOrderByIdAsc(t.getId()).stream().map(CommentDto::of).toList());
    }

    /** Tasks due in [from, to] across the caller's company (unified calendar, ERP manual 11.3). */
    @Transactional
    public List<TaskCalendarItem> calendar(Long companyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(EProjectError.PRJ_023);
        }
        List<Task> due = tasks.dueBetween(company, from, to);
        if (due.isEmpty()) {
            return List.of();
        }
        Ctx ctx = new Ctx(stages.ordered(company, EStageKind.TASK));
        Map<Long, String> names = projectRepo.findByIdIn(due.stream().map(Task::getProjectId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getId, Project::getName));
        return due.stream().map(t -> new TaskCalendarItem(t.getId().toString(), t.getProjectId().toString(),
                names.get(t.getProjectId()), t.getTitle(), t.getStartDate(), t.getDueDate(), ctx.stageName(t),
                ctx.done(t))).toList();
    }

    // ---------- writes ----------

    @Transactional
    public TaskDto create(Long projectId, TaskForm f) {
        Project p = projects.require(projectId);
        Task t = new Task();
        t.setCompanyId(p.getCompanyId());
        t.setProjectId(p.getId());
        apply(p, t, f);
        Stage stage = f.stageId() == null ? stages.firstStage(p.getCompanyId(), EStageKind.TASK)
                : stages.require(f.stageId(), EStageKind.TASK, p.getCompanyId());
        t.setStageId(stage.getId());
        t.setPosition(tasks.maxPosition(p.getId(), stage.getId()) + 1);
        tasks.save(t);
        activity.log(p.getCompanyId(), p.getId(), "TASK_CREATED", t.getTitle());
        return new Ctx(stages.ordered(p.getCompanyId(), EStageKind.TASK)).dto(t);
    }

    /** Full replacement of the fields; the stage changes only through move (stageId here is ignored). */
    @Transactional
    public TaskDto update(Long id, TaskForm f) {
        Task t = require(id);
        Project p = projects.require(t.getProjectId());
        apply(p, t, f);
        activity.log(p.getCompanyId(), p.getId(), "TASK_UPDATED", t.getTitle());
        return new Ctx(stages.ordered(p.getCompanyId(), EStageKind.TASK)).dto(t);
    }

    /** Moves the card to a stage and index; the last stage means done (counts in progress). */
    @Transactional
    public TaskDto move(Long id, MoveForm f) {
        Task t = require(id);
        Stage target = stages.require(f.stageId(), EStageKind.TASK, t.getCompanyId());
        Long from = t.getStageId();
        List<Task> column = new ArrayList<>(tasks.findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(
                t.getProjectId(), target.getId()));
        column.removeIf(x -> x.getId().equals(t.getId()));
        int index = f.position() == null ? column.size() : Math.min(f.position(), column.size());
        column.add(index, t);
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition(i + 1);
        }
        t.setStageId(target.getId());
        Ctx ctx = new Ctx(stages.ordered(t.getCompanyId(), EStageKind.TASK));
        if (!target.getId().equals(from)) {
            activity.log(t.getCompanyId(), t.getProjectId(), "TASK_MOVED", t.getTitle() + " -> " + target.getName());
        }
        return ctx.dto(t);
    }

    @Transactional
    public void delete(Long id) {
        Task t = require(id);
        t.setDeleted(true);
        activity.log(t.getCompanyId(), t.getProjectId(), "TASK_DELETED", t.getTitle());
    }

    // ---------- subtasks ----------

    @Transactional
    public SubtaskDto addSubtask(Long taskId, SubtaskForm f) {
        Task t = require(taskId);
        Subtask s = new Subtask();
        s.setCompanyId(t.getCompanyId());
        s.setTaskId(t.getId());
        s.setName(f.name().trim());
        s.setDone(f.done());
        return SubtaskDto.of(subtasks.save(s));
    }

    /** Full replacement (name and done flag). */
    @Transactional
    public SubtaskDto updateSubtask(Long id, SubtaskForm f) {
        Subtask s = requireSubtask(id);
        s.setName(f.name().trim());
        s.setDone(f.done());
        return SubtaskDto.of(s);
    }

    @Transactional
    public void deleteSubtask(Long id) {
        subtasks.delete(requireSubtask(id));
    }

    // ---------- comments ----------

    @Transactional(readOnly = true)
    public List<CommentDto> comments(Long taskId) {
        Task t = require(taskId);
        return comments.findByTaskIdAndDeletedFalseOrderByIdAsc(t.getId()).stream().map(CommentDto::of).toList();
    }

    @Transactional
    public CommentDto addComment(Long taskId, CommentForm f) {
        Task t = require(taskId);
        TaskComment c = new TaskComment();
        c.setCompanyId(t.getCompanyId());
        c.setTaskId(t.getId());
        c.setAuthorUserId(CurrentUser.find().map(ErpPrincipal::userId).orElse(null));
        c.setBody(f.body().trim());
        comments.save(c);
        activity.log(t.getCompanyId(), t.getProjectId(), "TASK_COMMENTED", t.getTitle());
        return CommentDto.of(c);
    }

    /** TODO(assumption): anyone with PROJECT_TASK_MANAGE may delete any comment of the company, not only the author. */
    @Transactional
    public void deleteComment(Long id) {
        TaskComment c = comments.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_019));
        TenantGuard.assertSameCompany(c.getCompanyId(), EProjectError.PRJ_019);
        require(c.getTaskId());
        c.setDeleted(true);
    }

    // ---------- shared ----------

    /** A task of a live project of the caller's company; otherwise 404 (PRJ_016). */
    @Transactional(readOnly = true)
    public Task require(Long id) {
        Task t = tasks.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_016));
        TenantGuard.assertSameCompany(t.getCompanyId(), EProjectError.PRJ_016);
        try {
            projects.require(t.getProjectId());
        } catch (ApiException e) {
            throw ApiException.notFound(EProjectError.PRJ_016);
        }
        return t;
    }

    private Subtask requireSubtask(Long id) {
        Subtask s = subtasks.findById(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_018));
        TenantGuard.assertSameCompany(s.getCompanyId(), EProjectError.PRJ_018);
        require(s.getTaskId());
        return s;
    }

    private void apply(Project p, Task t, TaskForm f) {
        if (f.startDate() != null && f.dueDate() != null && f.dueDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EProjectError.PRJ_025);
        }
        if (f.milestoneId() != null) {
            Milestone m = milestones.findByIdAndDeletedFalse(f.milestoneId())
                    .orElseThrow(() -> ApiException.badRequest(EProjectError.PRJ_011));
            if (!m.getProjectId().equals(p.getId())) {
                throw ApiException.badRequest(EProjectError.PRJ_011);
            }
        }
        Set<Long> assignees = new LinkedHashSet<>(f.assigneeIds() == null ? List.of() : f.assigneeIds());
        for (Long u : assignees) {
            if (!p.getUserIds().contains(u)) {
                throw ApiException.badRequest(EProjectError.PRJ_017, u);
            }
        }
        t.setMilestoneId(f.milestoneId());
        t.setTitle(f.title().trim());
        t.setPriority(f.priority() == null ? EPriority.MEDIUM : f.priority());
        t.setStartDate(f.startDate());
        t.setDueDate(f.dueDate());
        t.setDescription(f.description());
        t.getAssigneeIds().clear();
        t.getAssigneeIds().addAll(assignees);
    }

    /** Stage names and the done stage of one company, looked up once per answer. */
    static final class Ctx {
        private final Map<Long, Stage> byId;
        private final Long doneId;

        Ctx(List<Stage> ordered) {
            this.byId = ordered.stream().collect(Collectors.toMap(Stage::getId, Function.identity()));
            this.doneId = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).getId();
        }

        boolean done(Task t) {
            return t.getStageId() != null && t.getStageId().equals(doneId);
        }

        String stageName(Task t) {
            Stage s = byId.get(t.getStageId());
            return s == null ? null : s.getName();
        }

        TaskDto dto(Task t) {
            Long duration = t.getStartDate() == null || t.getDueDate() == null ? null
                    : ChronoUnit.DAYS.between(t.getStartDate(), t.getDueDate()) + 1;
            return new TaskDto(t.getId() == null ? null : t.getId().toString(), t.getProjectId().toString(),
                    t.getMilestoneId() == null ? null : t.getMilestoneId().toString(), t.getTitle(), t.getPriority(),
                    t.getStageId() == null ? null : t.getStageId().toString(), stageName(t), t.getPosition(), done(t),
                    t.getAssigneeIds().stream().map(String::valueOf).toList(), t.getStartDate(), t.getDueDate(),
                    duration, t.getDescription(), t.getCreatedDate());
        }
    }
}
