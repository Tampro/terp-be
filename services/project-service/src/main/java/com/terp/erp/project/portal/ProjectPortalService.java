package com.terp.erp.project.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.client.AccountingClient;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.MilestoneRepository;
import com.terp.erp.project.project.Project;
import com.terp.erp.project.project.ProjectEnums.EMilestoneStatus;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.project.ProjectRepository;
import com.terp.erp.project.project.ProjectService;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Client portal (read-only): projects whose clients include the accounting customer linked to the caller.
 * Hidden: budget, team (user ids), other clients, activity log, attachments, milestone cost and summary, task
 * assignees, descriptions, subtasks, comments, and bugs. Another customer's project answers 404 (PRJ_006).
 * TODO(assumption): the project description and the task list are customer-facing; confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class ProjectPortalService {
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final TaskRepository tasks;
    private final StageService stages;
    private final ProjectService projectService;
    private final AccountingClient accounting;

    public record PortalProject(String id, String name, String description, LocalDate startDate, LocalDate endDate,
                                EProjectStatus status, boolean overdue) {
    }

    public record PortalProjectDetail(PortalProject project, int progress, long taskCount, long doneTaskCount) {
    }

    public record PortalMilestone(String id, String title, LocalDate startDate, LocalDate endDate,
                                  EMilestoneStatus status, int progress) {
    }

    public record PortalTask(String id, String title, EPriority priority, String stageName, boolean done,
                             LocalDate startDate, LocalDate dueDate) {
    }

    @Transactional(readOnly = true)
    public Page<PortalProject> list(Pageable pageable) {
        Long customer = accounting.myCustomerId();
        return projects.findOfClient(CurrentUser.get().companyId(), customer, pageable).map(this::dto);
    }

    /** Write transaction: progress needs the done stage, created on first use. */
    @Transactional
    public PortalProjectDetail get(Long id) {
        Project p = requireMine(id);
        long total = tasks.countByProjectIdAndDeletedFalse(p.getId());
        long done = tasks.countByProjectIdAndStageIdAndDeletedFalse(p.getId(), stages.doneStageId(p.getCompanyId()));
        return new PortalProjectDetail(dto(p), ProjectService.percent(done, total), total, done);
    }

    @Transactional(readOnly = true)
    public List<PortalMilestone> milestones(Long id) {
        Project p = requireMine(id);
        return milestones.findByProjectIdAndDeletedFalseOrderByStartDateAscIdAsc(p.getId()).stream()
                .map(m -> new PortalMilestone(m.getId().toString(), m.getTitle(), m.getStartDate(), m.getEndDate(),
                        m.getStatus(), m.getProgress())).toList();
    }

    /** Write transaction: the stage list is created on first use. */
    @Transactional
    public List<PortalTask> tasks(Long id) {
        Project p = requireMine(id);
        List<Stage> ordered = stages.ordered(p.getCompanyId(), EStageKind.TASK);
        Map<Long, String> names = ordered.stream().collect(Collectors.toMap(Stage::getId, Stage::getName));
        Long doneId = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).getId();
        return tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(p.getId()).stream()
                .map(t -> new PortalTask(t.getId().toString(), t.getTitle(), t.getPriority(), names.get(t.getStageId()),
                        Objects.equals(t.getStageId(), doneId), t.getStartDate(), t.getDueDate())).toList();
    }

    private Project requireMine(Long id) {
        Long customer = accounting.myCustomerId();
        return projects.findByIdAndDeletedFalse(id)
                .filter(p -> Objects.equals(p.getCompanyId(), CurrentUser.get().companyId())
                        && p.getClientIds().contains(customer))
                .orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_006));
    }

    private PortalProject dto(Project p) {
        return new PortalProject(p.getId().toString(), p.getName(), p.getDescription(), p.getStartDate(),
                p.getEndDate(), p.getStatus(), p.overdue(projectService.today()));
    }
}
