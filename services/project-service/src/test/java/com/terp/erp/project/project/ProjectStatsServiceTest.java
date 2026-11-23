package com.terp.erp.project.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.TestLogin;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.task.Bug;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageRepository;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStatsServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);

    private final ProjectRepository projectRepo = mock(ProjectRepository.class);
    private final TaskRepository taskRepo = mock(TaskRepository.class);
    private final BugRepository bugRepo = mock(BugRepository.class);
    private final StageRepository stageRepo = mock(StageRepository.class);
    private final StageService stages = new StageService(stageRepo, taskRepo, bugRepo);
    private final ProjectStatsService service = new ProjectStatsService(projectRepo, taskRepo, bugRepo, stages, CLOCK);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubStages() {
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(10L, EStageKind.TASK))
                .thenReturn(stages(EStageKind.TASK, 101L));
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(10L, EStageKind.BUG))
                .thenReturn(stages(EStageKind.BUG, 201L));
    }

    @Test
    void countsOverTheCallersCompany() {
        TestLogin.as(10L, "PROJECT_VIEW");
        stubStages();
        Project a = project(1L, EProjectStatus.ONGOING, LocalDate.parse("2026-01-01"), List.of(7L, 8L), List.of(3L));
        Project b = project(2L, EProjectStatus.FINISHED, LocalDate.parse("2026-01-01"), List.of(8L), List.of(3L, 4L));
        Project c = project(3L, EProjectStatus.ON_HOLD, null, List.of(), List.of());
        when(projectRepo.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(a, b, c));
        when(taskRepo.findByProjectIdInAndDeletedFalse(List.of(1L, 2L, 3L))).thenReturn(List.of(
                task(103L, EPriority.HIGH, "2026-02-10T05:00:00Z", "2026-03-01T05:00:00Z", 7L),
                task(101L, EPriority.LOW, "2026-02-11T05:00:00Z", "2026-02-11T05:00:00Z", 7L, 8L),
                // created 31 Jan 20:00 UTC = 1 Feb 03:00 in Ho Chi Minh
                task(102L, EPriority.LOW, "2026-01-31T20:00:00Z", "2026-01-31T20:00:00Z", 8L),
                task(103L, EPriority.MEDIUM, "2025-12-01T00:00:00Z", "2025-12-01T00:00:00Z")));
        when(bugRepo.findByProjectIdInAndDeletedFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(bug(201L), bug(203L), bug(202L)));

        var s = service.stats(99L, 2026, null);

        verify(projectRepo).findByCompanyIdAndDeletedFalse(10L);
        verify(projectRepo, never()).listOwn(anyLong(), any(), any());
        assertThat(s.scope()).isEqualTo("ALL");
        assertThat(s.totalProjects()).isEqualTo(3);
        assertThat(s.overdueProjects()).isEqualTo(1);
        assertThat(s.totalTasks()).isEqualTo(4);
        assertThat(s.completedTasks()).isEqualTo(2);
        assertThat(s.taskCompletionPercent()).isEqualTo(50);
        assertThat(s.activeBugs()).isEqualTo(2);
        assertThat(s.teamSize()).isEqualTo(2);
        assertThat(s.clients()).isEqualTo(2);
        assertThat(s.monthly()).hasSize(12);
        assertThat(s.monthly().get(1).created()).isEqualTo(3);
        assertThat(s.monthly().get(0).created()).isZero();
        assertThat(s.monthly().get(2).completed()).isEqualTo(1);
        assertThat(s.byStatus()).containsEntry(EProjectStatus.ONGOING, 1L).containsEntry(EProjectStatus.ON_HOLD, 1L)
                .containsEntry(EProjectStatus.FINISHED, 1L);
        assertThat(s.byPriority()).containsEntry(EPriority.LOW, 2L).containsEntry(EPriority.HIGH, 1L)
                .containsEntry(EPriority.MEDIUM, 1L);
        assertThat(s.teamPerformance()).hasSize(2);
        var first = s.teamPerformance().get(0);
        assertThat(first.assigned()).isEqualTo(2);
        assertThat(s.teamPerformance()).anySatisfy(u -> {
            assertThat(u.userId()).isEqualTo("7");
            assertThat(u.done()).isEqualTo(1);
            assertThat(u.progress()).isEqualTo(50);
        });
    }

    @Test
    void ownScopeUsesTheOwnQueryAndSkipsTasksWhenNothingIsVisible() {
        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW_OWN");
        stubStages();
        when(projectRepo.listOwn(10L, 7L, "kc-7")).thenReturn(List.of());

        var s = service.stats(null, null, "UTC");

        verify(projectRepo, never()).findByCompanyIdAndDeletedFalse(anyLong());
        verify(taskRepo, never()).findByProjectIdInAndDeletedFalse(any());
        assertThat(s.scope()).isEqualTo("OWN");
        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.totalProjects()).isZero();
        assertThat(s.taskCompletionPercent()).isZero();
    }

    @Test
    void unknownTimeZoneIsAValidationError() {
        TestLogin.as(10L, "PROJECT_VIEW");
        assertThatThrownBy(() -> service.stats(null, null, "Mars/Base"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode().toString()).isEqualTo("COMMON_001"));
    }

    private static Project project(Long id, EProjectStatus status, LocalDate end, List<Long> users, List<Long> clients) {
        Project p = new Project();
        p.setId(id);
        p.setCompanyId(10L);
        p.setStatus(status);
        p.setEndDate(end);
        p.getUserIds().addAll(users);
        p.getClientIds().addAll(clients);
        return p;
    }

    private static Task task(Long stage, EPriority priority, String created, String updated, Long... assignees) {
        Task t = new Task();
        t.setStageId(stage);
        t.setPriority(priority);
        t.setCreatedDate(Instant.parse(created));
        t.setUpdatedDate(Instant.parse(updated));
        t.getAssigneeIds().addAll(List.of(assignees));
        return t;
    }

    private static Bug bug(Long stage) {
        Bug b = new Bug();
        b.setStageId(stage);
        return b;
    }

    private static List<Stage> stages(EStageKind kind, long firstId) {
        List<Stage> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Stage s = new Stage();
            s.setId(firstId + i);
            s.setCompanyId(10L);
            s.setKind(kind);
            s.setName(List.of("Todo", "Doing", "Done").get(i));
            s.setPosition(i + 1);
            list.add(s);
        }
        return list;
    }
}
