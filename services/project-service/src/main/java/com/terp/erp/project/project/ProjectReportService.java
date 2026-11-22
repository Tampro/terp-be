package com.terp.erp.project.project;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.project.project.ProjectEnums.EMilestoneStatus;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Project report (FR-PRJ-006): status, budget, timeline, tasks by stage and priority, per user, milestones. */
@Service
@RequiredArgsConstructor
public class ProjectReportService {
    private final ProjectService projects;
    private final TaskRepository tasks;
    private final MilestoneRepository milestones;
    private final StageService stages;

    public record StageCount(String stageId, String stageName, boolean done, long count) {
    }

    public record UserProgress(String userId, long assigned, long done, int progress) {
    }

    public record ProjectReport(String projectId, String name, EProjectStatus status, BigDecimal budget,
                                BigDecimal milestoneCost, LocalDate startDate, LocalDate endDate, Long daysLeft,
                                boolean overdue, long taskCount, long doneTaskCount, int progress,
                                List<StageCount> tasksByStage, Map<EPriority, Long> tasksByPriority,
                                List<UserProgress> users, Map<EMilestoneStatus, Long> milestonesByStatus) {
    }

    /** Write transaction only because the default stages may be created on first use. */
    @Transactional
    public ProjectReport report(Long id) {
        Project p = projects.require(id);
        List<Stage> ordered = stages.ordered(p.getCompanyId(), EStageKind.TASK);
        Long doneId = ordered.get(ordered.size() - 1).getId();
        List<Task> all = tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(p.getId());

        List<StageCount> byStage = new ArrayList<>();
        for (Stage s : ordered) {
            long n = all.stream().filter(t -> s.getId().equals(t.getStageId())).count();
            byStage.add(new StageCount(s.getId().toString(), s.getName(), s.getId().equals(doneId), n));
        }
        Map<EPriority, Long> byPriority = new EnumMap<>(EPriority.class);
        for (EPriority pr : EPriority.values()) {
            byPriority.put(pr, all.stream().filter(t -> t.getPriority() == pr).count());
        }
        // Every team member is listed, also those without tasks; former members still holding tasks too.
        Map<Long, long[]> perUser = new LinkedHashMap<>();
        p.getUserIds().forEach(u -> perUser.put(u, new long[2]));
        for (Task t : all) {
            for (Long u : t.getAssigneeIds()) {
                long[] c = perUser.computeIfAbsent(u, k -> new long[2]);
                c[0]++;
                if (doneId.equals(t.getStageId())) {
                    c[1]++;
                }
            }
        }
        List<UserProgress> users = perUser.entrySet().stream().map(e -> new UserProgress(e.getKey().toString(),
                e.getValue()[0], e.getValue()[1], ProjectService.percent(e.getValue()[1], e.getValue()[0]))).toList();

        List<Milestone> ms = milestones.findByProjectIdAndDeletedFalseOrderByStartDateAscIdAsc(p.getId());
        Map<EMilestoneStatus, Long> byStatus = new EnumMap<>(EMilestoneStatus.class);
        for (EMilestoneStatus st : EMilestoneStatus.values()) {
            byStatus.put(st, ms.stream().filter(m -> m.getStatus() == st).count());
        }
        BigDecimal cost = ms.stream().map(Milestone::getCost).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long done = all.stream().filter(t -> doneId.equals(t.getStageId())).count();
        return new ProjectReport(p.getId().toString(), p.getName(), p.getStatus(), p.getBudget(), cost,
                p.getStartDate(), p.getEndDate(), projects.daysLeft(p), p.overdue(projects.today()), all.size(), done,
                ProjectService.percent(done, all.size()), byStage, byPriority, users, byStatus);
    }
}
