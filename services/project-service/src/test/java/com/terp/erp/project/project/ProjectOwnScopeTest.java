package com.terp.erp.project.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.TestLogin;
import com.terp.erp.project.client.AccountingClient;
import com.terp.erp.project.client.IamClient;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.portal.ClientPortalController;
import com.terp.erp.project.portal.ProjectPortalService;
import com.terp.erp.project.task.BugController;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageRepository;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.SubtaskRepository;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskCommentRepository;
import com.terp.erp.project.task.TaskController;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.project.task.TaskService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PROJECT_*_OWN scopes and the client portal. */
class ProjectOwnScopeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
    private final ProjectRepository projectRepo = mock(ProjectRepository.class);
    private final MilestoneRepository milestoneRepo = mock(MilestoneRepository.class);
    private final StageRepository stageRepo = mock(StageRepository.class);
    private final TaskRepository taskRepo = mock(TaskRepository.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final StageService stages = new StageService(stageRepo, taskRepo, mock(BugRepository.class));
    private final ProjectService projects = new ProjectService(projectRepo, taskRepo, stages, mock(IamClient.class),
            accounting, mock(ActivityService.class), mock(com.terp.erp.outbox.OutboxPublisher.class), CLOCK);
    private final TaskService taskService = new TaskService(projects, projectRepo, milestoneRepo, stages, taskRepo,
            mock(SubtaskRepository.class), mock(TaskCommentRepository.class), mock(ActivityService.class),
            mock(com.terp.erp.outbox.OutboxPublisher.class));
    private final ProjectPortalService portal = new ProjectPortalService(projectRepo, milestoneRepo, taskRepo, stages,
            projects, accounting);
    private final List<Stage> taskStages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 2; i++) {
            Stage s = new Stage();
            s.setId(101L + i);
            s.setCompanyId(10L);
            s.setName(i == 0 ? "Todo" : "Done");
            taskStages.add(s);
        }
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(eq(10L), any()))
                .thenReturn(taskStages);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Project project(long id, String createdBy, Long... team) {
        Project p = new Project();
        p.setId(id);
        p.setCompanyId(10L);
        p.setName("Test project " + id);
        p.setCreatedBy(createdBy);
        p.getUserIds().addAll(List.of(team));
        when(projectRepo.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Task task(long id, long projectId, String createdBy, Long... assignees) {
        Task t = new Task();
        t.setId(id);
        t.setCompanyId(10L);
        t.setProjectId(projectId);
        t.setStageId(101L);
        t.setTitle("Test task");
        t.setCreatedBy(createdBy);
        t.getAssigneeIds().addAll(List.of(assignees));
        when(taskRepo.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void ownListUsesTheTeamQueryAndFullListIsUnaffected() {
        when(projectRepo.searchOwn(eq(10L), any(), any(), eq(7L), eq("kc-7"), any())).thenReturn(Page.empty());
        when(projectRepo.search(eq(10L), any(), any(), any())).thenReturn(Page.empty());

        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW_OWN");
        projects.list(null, null, null, Pageable.unpaged());
        verify(projectRepo, never()).search(any(), any(), any(), any());

        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW");
        projects.list(null, null, null, Pageable.unpaged());
        verify(projectRepo).search(eq(10L), any(), any(), any());
    }

    @Test
    void viewOwnSeesTeamProjectsOnly() {
        project(1L, "kc-12", 12L);
        project(2L, "kc-12", 7L);
        project(3L, "kc-7");
        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW_OWN");

        assertThatThrownBy(() -> projects.detail(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_006);
        assertThat(projects.detail(2L).project().id()).isEqualTo("2");
        assertThat(projects.detail(3L).project().id()).isEqualTo("3");

        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW");
        assertThat(projects.detail(1L).project().id()).isEqualTo("1");
    }

    @Test
    void manageOwnChangesOnlyProjectsICreated() {
        Project onTeam = project(2L, "kc-12", 7L);
        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW_OWN", "PROJECT_MANAGE_OWN");

        assertThatThrownBy(() -> projects.delete(2L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_006);
        assertThat(onTeam.isDeleted()).isFalse();

        Project mine = project(3L, "kc-7");
        projects.delete(3L);
        assertThat(mine.isDeleted()).isTrue();
    }

    @Test
    void taskManageOwnTouchesOnlyMyTasks() {
        project(1L, "kc-12", 7L, 12L);
        Task other = task(50L, 1L, "kc-12", 12L);
        task(51L, 1L, "kc-12", 7L);
        TestLogin.user(7L, "kc-7", 10L, "PROJECT_VIEW_OWN", "PROJECT_TASK_MANAGE_OWN");

        assertThatThrownBy(() -> taskService.delete(50L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_016);
        assertThat(other.isDeleted()).isFalse();
        // Viewing follows the project: a teammate's task is visible, only not writable.
        assertThat(taskService.detail(50L).task().id()).isEqualTo("50");
        taskService.delete(51L);
    }

    // ---------- portal ----------

    @Test
    void portalListsAndShowsOnlyMyCustomersProjects() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenReturn(5L);
        Project mine = project(1L, "kc-12");
        mine.getClientIds().add(5L);
        Project other = project(2L, "kc-12");
        other.getClientIds().add(6L);
        when(projectRepo.findOfClient(eq(10L), eq(5L), any())).thenReturn(new PageImpl<>(List.of(mine)));

        assertThat(portal.list(Pageable.unpaged()).getContent()).extracting(ProjectPortalService.PortalProject::id)
                .containsExactly("1");
        assertThat(portal.get(1L).project().name()).isEqualTo("Test project 1");
        assertThatThrownBy(() -> portal.get(2L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_006);
        assertThatThrownBy(() -> portal.tasks(2L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_006);
    }

    @Test
    void portalNotLinkedAnswers404() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");
        when(accounting.myCustomerId()).thenThrow(ApiException.notFound(EProjectError.PRJ_026));

        assertThatThrownBy(() -> portal.list(Pageable.unpaged()))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_026);
    }

    // ---------- annotations ----------

    private static List<Method> handlers(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> Arrays.stream(m.getAnnotations())
                        .anyMatch(a -> a.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();
    }

    @Test
    void handlersAcceptTheOwnCodeAndPortalNeedsPortalClient() {
        for (Class<?> c : List.of(ProjectController.class, TaskController.class, BugController.class)) {
            for (Method m : handlers(c)) {
                String v = m.getAnnotation(PreAuthorize.class).value();
                if (m.getName().equals("calendar")) {
                    assertThat(v).isEqualTo("@PermissionChecker.has('PROJECT_VIEW')");
                } else {
                    assertThat(v).as(c.getSimpleName() + "." + m.getName())
                            .matches("@PermissionChecker\\.hasAny\\('PROJECT_(VIEW|MANAGE|TASK_MANAGE)',"
                                    + "'PROJECT_\\1_OWN'\\)");
                }
            }
        }
        assertThat(handlers(ClientPortalController.class)).hasSize(4).allSatisfy(m -> {
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.has('PORTAL_CLIENT')");
            assertThat(m.isAnnotationPresent(GetMapping.class)).isTrue();
        });
    }
}
