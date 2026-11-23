package com.terp.erp.project.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.task.Bug;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Project dashboard figures (ERP manual 5.1), over the projects the caller can see. */
@Service
@RequiredArgsConstructor
public class ProjectStatsService {
    // TODO(assumption): default zone when the caller passes none; IAM keeps a defaultTimezone per company.
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final BugRepository bugs;
    private final StageService stages;
    private final Clock clock;

    public record MonthRow(int month, long created, long completed) {
    }

    public record UserRow(String userId, long assigned, long done, int progress) {
    }

    @Schema(description = "Totals are all-time over the visible projects; monthly is for the year")
    public record ProjectStats(int year, @Schema(description = "ALL or OWN") String scope, long totalProjects,
                               long overdueProjects, long totalTasks, long completedTasks, int taskCompletionPercent,
                               long activeBugs, long teamSize, long clients, List<MonthRow> monthly,
                               @Schema(description = "C-05: manual Ongoing / Finished / On Hold = ONGOING / FINISHED / ON_HOLD")
                               Map<EProjectStatus, Long> byStatus, Map<EPriority, Long> byPriority,
                               List<UserRow> teamPerformance) {
    }

    /** Write transaction only because the default stages may be created on first use. */
    @Transactional
    public ProjectStats stats(Long companyId, Integer year, String tz) {
        ZoneId zone = zone(tz);
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        boolean all = ProjectScope.seesAll();
        List<Project> visible = all ? projects.findByCompanyIdAndDeletedFalse(company)
                : projects.listOwn(company, OwnScope.userId(), OwnScope.sub());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int y = year == null ? today.getYear() : year;
        List<Long> ids = visible.stream().map(Project::getId).toList();
        List<Task> ts = ids.isEmpty() ? List.of() : tasks.findByProjectIdInAndDeletedFalse(ids);
        List<Bug> bs = ids.isEmpty() ? List.of() : bugs.findByProjectIdInAndDeletedFalse(ids);
        Long doneId = stages.doneStageId(company);
        List<Stage> bugStages = stages.ordered(company, EStageKind.BUG);
        Long bugDoneId = bugStages.get(bugStages.size() - 1).getId();

        Map<EProjectStatus, Long> byStatus = new EnumMap<>(EProjectStatus.class);
        for (EProjectStatus s : EProjectStatus.values()) {
            byStatus.put(s, visible.stream().filter(p -> p.getStatus() == s).count());
        }
        long overdue = visible.stream().filter(p -> p.overdue(today)).count();
        Set<Long> team = new HashSet<>();
        Set<Long> clients = new HashSet<>();
        visible.forEach(p -> {
            team.addAll(p.getUserIds());
            clients.addAll(p.getClientIds());
        });

        Map<EPriority, Long> byPriority = new EnumMap<>(EPriority.class);
        for (EPriority pr : EPriority.values()) {
            byPriority.put(pr, ts.stream().filter(t -> t.getPriority() == pr).count());
        }
        long[] created = new long[13];
        long[] completed = new long[13];
        long done = 0;
        Map<Long, long[]> perUser = new LinkedHashMap<>();
        for (Task t : ts) {
            boolean isDone = doneId.equals(t.getStageId());
            if (isDone) {
                done++;
            }
            created[month(t.getCreatedDate(), zone, y)]++;
            // TODO(assumption): tasks keep no completion time; a done task counts in the month it was last updated.
            if (isDone) {
                completed[month(t.getUpdatedDate(), zone, y)]++;
            }
            for (Long u : t.getAssigneeIds()) {
                long[] v = perUser.computeIfAbsent(u, k -> new long[2]);
                v[0]++;
                if (isDone) {
                    v[1]++;
                }
            }
        }
        List<MonthRow> monthly = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            monthly.add(new MonthRow(i, created[i], completed[i]));
        }
        List<UserRow> users = perUser.entrySet().stream()
                .map(e -> new UserRow(e.getKey().toString(), e.getValue()[0], e.getValue()[1],
                        ProjectService.percent(e.getValue()[1], e.getValue()[0])))
                .sorted(Comparator.comparingLong(UserRow::assigned).reversed()).toList();
        long activeBugs = bs.stream().filter(b -> !bugDoneId.equals(b.getStageId())).count();
        return new ProjectStats(y, all ? "ALL" : "OWN", visible.size(), overdue, ts.size(), done,
                ProjectService.percent(done, ts.size()), activeBugs, team.size(), clients.size(), monthly, byStatus,
                byPriority, users);
    }

    /** Month 1-12 of the instant in the zone when it falls in the year, else 0 (slot 0 is ignored). */
    private static int month(Instant at, ZoneId zone, int year) {
        if (at == null) {
            return 0;
        }
        ZonedDateTime z = at.atZone(zone);
        return z.getYear() == year ? z.getMonthValue() : 0;
    }

    static ZoneId zone(String tz) {
        if (tz == null || tz.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(tz.trim());
        } catch (DateTimeException e) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
    }
}
