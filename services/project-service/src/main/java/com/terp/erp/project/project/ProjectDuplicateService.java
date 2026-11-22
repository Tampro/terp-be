package com.terp.erp.project.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectService.ProjectDto;
import com.terp.erp.project.task.Bug;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.Subtask;
import com.terp.erp.project.task.SubtaskRepository;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskComment;
import com.terp.erp.project.task.TaskCommentRepository;
import com.terp.erp.project.task.TaskRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Duplicate a project (FR-PRJ-002). The copy always takes name, dates, budget and description, starts ONGOING,
 * and takes only the parts chosen. Ids already on the source were checked when added, so nothing is re-asked to
 * IAM, accounting or media. Task assignees are kept only when the team is copied (they must be team members).
 */
@Service
@RequiredArgsConstructor
public class ProjectDuplicateService {
    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final AttachmentRepository attachments;
    private final ActivityRepository activities;
    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final TaskCommentRepository comments;
    private final BugRepository bugs;
    private final ActivityService activity;

    public record DuplicateForm(
            @Schema(description = "Name of the copy; null = \"<name> (copy)\"") @Size(max = 191) String name,
            @Schema(description = "Copy tasks with their subtasks and comments") boolean tasks,
            @Schema(description = "Copy bugs") boolean bugs,
            @Schema(description = "Copy the activity log") boolean activity,
            @Schema(description = "Copy the team members") boolean team,
            @Schema(description = "Copy the clients") boolean clients,
            @Schema(description = "Copy the milestones") boolean milestones,
            @Schema(description = "Copy the attachments") boolean files) {
    }

    @Transactional
    public ProjectDto duplicate(Long id, DuplicateForm f) {
        Project src = projectService.require(id);
        Project p = new Project();
        p.setCompanyId(src.getCompanyId());
        String name = f.name() == null || f.name().isBlank() ? src.getName() + " (copy)" : f.name().trim();
        p.setName(name.length() > 191 ? name.substring(0, 191) : name);
        p.setStartDate(src.getStartDate());
        p.setEndDate(src.getEndDate());
        p.setBudget(src.getBudget());
        p.setDescription(src.getDescription());
        p.setStatus(EProjectStatus.ONGOING);
        if (f.team()) {
            p.getUserIds().addAll(src.getUserIds());
        }
        if (f.clients()) {
            p.getClientIds().addAll(src.getClientIds());
        }
        projects.save(p);

        Map<Long, Long> milestoneIds = new HashMap<>();
        if (f.milestones()) {
            for (Milestone m : milestones.findByProjectIdAndDeletedFalseOrderByStartDateAscIdAsc(src.getId())) {
                Milestone c = new Milestone();
                c.setCompanyId(p.getCompanyId());
                c.setProjectId(p.getId());
                c.setTitle(m.getTitle());
                c.setCost(m.getCost());
                c.setStartDate(m.getStartDate());
                c.setEndDate(m.getEndDate());
                c.setSummary(m.getSummary());
                c.setStatus(m.getStatus());
                c.setProgress(m.getProgress());
                milestones.save(c);
                milestoneIds.put(m.getId(), c.getId());
            }
        }
        if (f.tasks()) {
            copyTasks(src, p, milestoneIds, f.team());
        }
        if (f.bugs()) {
            for (Bug b : bugs.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(src.getId())) {
                Bug c = new Bug();
                c.setCompanyId(p.getCompanyId());
                c.setProjectId(p.getId());
                c.setTitle(b.getTitle());
                c.setPriority(b.getPriority());
                c.setAssigneeUserId(f.team() ? b.getAssigneeUserId() : null);
                c.setStageId(b.getStageId());
                c.setPosition(b.getPosition());
                c.setDescription(b.getDescription());
                bugs.save(c);
            }
        }
        if (f.files()) {
            for (Attachment a : attachments.findByProjectIdOrderByIdAsc(src.getId())) {
                Attachment c = new Attachment();
                c.setCompanyId(p.getCompanyId());
                c.setProjectId(p.getId());
                c.setFileId(a.getFileId());
                c.setName(a.getName());
                attachments.save(c);
            }
        }
        if (f.activity()) {
            for (Activity a : activities.findByProjectIdOrderByIdAsc(src.getId())) {
                Activity c = new Activity();
                c.setCompanyId(p.getCompanyId());
                c.setProjectId(p.getId());
                c.setActorUserId(a.getActorUserId());
                c.setAction(a.getAction());
                c.setDetail(a.getDetail());
                activities.save(c);
            }
        }
        activity.log(p.getCompanyId(), p.getId(), "PROJECT_DUPLICATED", "from " + src.getName());
        return projectService.dto(p);
    }

    private void copyTasks(Project src, Project p, Map<Long, Long> milestoneIds, boolean team) {
        for (Task t : tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(src.getId())) {
            Task c = new Task();
            c.setCompanyId(p.getCompanyId());
            c.setProjectId(p.getId());
            c.setMilestoneId(t.getMilestoneId() == null ? null : milestoneIds.get(t.getMilestoneId()));
            c.setTitle(t.getTitle());
            c.setPriority(t.getPriority());
            c.setStageId(t.getStageId());
            c.setPosition(t.getPosition());
            c.setStartDate(t.getStartDate());
            c.setDueDate(t.getDueDate());
            c.setDescription(t.getDescription());
            if (team) {
                c.getAssigneeIds().addAll(t.getAssigneeIds());
            }
            tasks.save(c);
            for (Subtask s : subtasks.findByTaskIdOrderByIdAsc(t.getId())) {
                Subtask cs = new Subtask();
                cs.setCompanyId(p.getCompanyId());
                cs.setTaskId(c.getId());
                cs.setName(s.getName());
                cs.setDone(s.isDone());
                subtasks.save(cs);
            }
            for (TaskComment m : comments.findByTaskIdAndDeletedFalseOrderByIdAsc(t.getId())) {
                TaskComment cm = new TaskComment();
                cm.setCompanyId(p.getCompanyId());
                cm.setTaskId(c.getId());
                cm.setAuthorUserId(m.getAuthorUserId());
                cm.setBody(m.getBody());
                comments.save(cm);
            }
        }
    }
}
