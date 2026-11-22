package com.terp.erp.project.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.TestLogin;
import com.terp.erp.project.client.AccountingClient;
import com.terp.erp.project.client.IamClient;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ProjectDuplicateService.DuplicateForm;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.project.ProjectService.ProjectForm;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageRepository;
import com.terp.erp.project.task.StageService;
import com.terp.erp.project.task.Subtask;
import com.terp.erp.project.task.SubtaskRepository;
import com.terp.erp.project.task.Task;
import com.terp.erp.project.task.TaskComment;
import com.terp.erp.project.task.TaskCommentRepository;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.project.task.TaskService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectModuleTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);

    private final ProjectRepository projectRepo = mock(ProjectRepository.class);
    private final MilestoneRepository milestoneRepo = mock(MilestoneRepository.class);
    private final AttachmentRepository attachmentRepo = mock(AttachmentRepository.class);
    private final ActivityRepository activityRepo = mock(ActivityRepository.class);
    private final StageRepository stageRepo = mock(StageRepository.class);
    private final TaskRepository taskRepo = mock(TaskRepository.class);
    private final SubtaskRepository subtaskRepo = mock(SubtaskRepository.class);
    private final TaskCommentRepository commentRepo = mock(TaskCommentRepository.class);
    private final BugRepository bugRepo = mock(BugRepository.class);
    private final IamClient iam = mock(IamClient.class);
    private final ActivityService activity = mock(ActivityService.class);

    private final StageService stages = new StageService(stageRepo, taskRepo, bugRepo);
    private final ProjectService projects = new ProjectService(projectRepo, taskRepo, stages, iam,
            mock(AccountingClient.class), activity, CLOCK);
    private final TaskService taskService = new TaskService(projects, projectRepo, milestoneRepo, stages, taskRepo,
            subtaskRepo, commentRepo, activity);
    private final ProjectReportService reports = new ProjectReportService(projects, taskRepo, milestoneRepo, stages);
    private final ProjectDuplicateService duplicates = new ProjectDuplicateService(projects, projectRepo, milestoneRepo,
            attachmentRepo, activityRepo, taskRepo, subtaskRepo, commentRepo, bugRepo, activity);

    private final Project project = new Project();
    private final List<Stage> taskStages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        project.setId(1L);
        project.setCompanyId(10L);
        project.setName("Website");
        project.getUserIds().addAll(Set.of(7L, 8L));
        when(projectRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(project));
        for (int i = 0; i < 4; i++) {
            Stage s = new Stage();
            s.setId(101L + i);
            s.setCompanyId(10L);
            s.setKind(EStageKind.TASK);
            s.setName(List.of("Todo", "In Progress", "Review", "Done").get(i));
            s.setPosition(i + 1);
            taskStages.add(s);
            when(stageRepo.findByIdAndDeletedFalse(s.getId())).thenReturn(Optional.of(s));
        }
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(10L, EStageKind.TASK))
                .thenReturn(taskStages);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectAndTaskOfAnotherCompanyAreNotFound() {
        Task t = task(50L, 101L, 1);
        when(taskRepo.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(t));
        TestLogin.as(20L, "PROJECT_VIEW", "PROJECT_TASK_MANAGE");

        assertThatThrownBy(() -> projects.detail(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_006);
        assertThatThrownBy(() -> taskService.detail(50L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_016);
        assertThatThrownBy(() -> taskService.move(50L, new TaskService.MoveForm(104L, 0)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_016);
    }

    @Test
    void createRefusesEndBeforeStartAndChecksEveryMemberThroughIam() {
        TestLogin.as(10L, "PROJECT_MANAGE");
        assertThatThrownBy(() -> projects.create(form(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1), List.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_007);

        when(iam.requireCompanyUser(99L, 10L)).thenThrow(ApiException.badRequest(EProjectError.PRJ_008, 99L));
        assertThatThrownBy(() -> projects.create(form(null, null, List.of(7L, 99L))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_008);
        verify(projectRepo, never()).save(any());

        ProjectService.ProjectDto dto = projects.create(form(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1), List.of(7L)));
        verify(iam, times(2)).requireCompanyUser(7L, 10L);
        assertThat(dto.companyId()).isEqualTo("10");
        assertThat(dto.userIds()).containsExactly("7");
        assertThat(dto.overdue()).isTrue();
    }

    @Test
    void taskAssigneeMustBeOnTheTeam() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        assertThatThrownBy(() -> taskService.create(1L, taskForm(List.of(7L, 42L), null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_017);
        verify(taskRepo, never()).save(any());

        TaskService.TaskDto dto = taskService.create(1L, taskForm(List.of(7L), null));
        assertThat(dto.stageName()).isEqualTo("Todo");
        assertThat(dto.done()).isFalse();
    }

    @Test
    void milestoneOfAnotherProjectIsRefused() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        Milestone m = new Milestone();
        m.setId(60L);
        m.setProjectId(2L);
        m.setCompanyId(10L);
        when(milestoneRepo.findByIdAndDeletedFalse(60L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> taskService.create(1L, taskForm(List.of(), 60L)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_011);
    }

    @Test
    void movingATaskToTheLastStageCountsAsDoneInProgress() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE", "PROJECT_VIEW");
        Task a = task(50L, 101L, 1);
        a.getAssigneeIds().add(7L);
        Task b = task(51L, 102L, 1);
        b.getAssigneeIds().add(8L);
        when(taskRepo.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(a));
        when(taskRepo.findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(1L, 104L)).thenReturn(List.of());
        when(taskRepo.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(1L)).thenReturn(List.of(a, b));

        TaskService.TaskDto moved = taskService.move(50L, new TaskService.MoveForm(104L, null));
        assertThat(moved.done()).isTrue();
        assertThat(a.getStageId()).isEqualTo(104L);
        verify(activity).log(eq(10L), eq(1L), eq("TASK_MOVED"), any());

        ProjectReportService.ProjectReport r = reports.report(1L);
        assertThat(r.progress()).isEqualTo(50);
        assertThat(r.doneTaskCount()).isEqualTo(1);
        assertThat(r.tasksByStage()).extracting(ProjectReportService.StageCount::count).containsExactly(0L, 1L, 0L, 1L);
        assertThat(r.users()).filteredOn(u -> u.userId().equals("7")).singleElement()
                .satisfies(u -> assertThat(u.progress()).isEqualTo(100));
        assertThat(r.users()).filteredOn(u -> u.userId().equals("8")).singleElement()
                .satisfies(u -> assertThat(u.done()).isZero());
    }

    @Test
    void aTaskStageCannotBeMovedOntoTheBugBoard() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        Stage bugStage = new Stage();
        bugStage.setId(201L);
        bugStage.setCompanyId(10L);
        bugStage.setKind(EStageKind.BUG);
        when(stageRepo.findByIdAndDeletedFalse(201L)).thenReturn(Optional.of(bugStage));
        when(taskRepo.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(task(50L, 101L, 1)));
        assertThatThrownBy(() -> taskService.move(50L, new TaskService.MoveForm(201L, 0)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_024);
    }

    @Test
    void duplicateCopiesOnlyTheChosenParts() {
        TestLogin.as(10L, "PROJECT_MANAGE");
        project.getClientIds().add(300L);
        Task a = task(50L, 102L, 1);
        a.getAssigneeIds().add(7L);
        Milestone m = new Milestone();
        m.setId(60L);
        a.setMilestoneId(60L);
        Subtask s = new Subtask();
        s.setName("Logo");
        s.setDone(true);
        TaskComment c = new TaskComment();
        c.setBody("ok");
        when(taskRepo.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(1L)).thenReturn(List.of(a));
        when(subtaskRepo.findByTaskIdOrderByIdAsc(50L)).thenReturn(List.of(s));
        when(commentRepo.findByTaskIdAndDeletedFalseOrderByIdAsc(50L)).thenReturn(List.of(c));
        when(milestoneRepo.findByProjectIdAndDeletedFalseOrderByStartDateAscIdAsc(1L)).thenReturn(List.of(m));

        ProjectService.ProjectDto copy = duplicates.duplicate(1L,
                new DuplicateForm(null, true, false, false, false, true, false, false));

        assertThat(copy.name()).isEqualTo("Website (copy)");
        assertThat(copy.userIds()).isEmpty();
        assertThat(copy.clientIds()).containsExactly("300");
        verify(taskRepo, times(1)).save(argThat(t -> t.getStageId().equals(102L) && t.getAssigneeIds().isEmpty()
                && t.getMilestoneId() == null));
        verify(subtaskRepo).save(argThat(x -> x.getName().equals("Logo") && x.isDone()));
        verify(commentRepo).save(argThat(x -> x.getBody().equals("ok")));
        verify(milestoneRepo, never()).save(any());
        verify(bugRepo, never()).save(any());
        verify(attachmentRepo, never()).save(any());
        verify(activityRepo, never()).save(any());
        verify(iam, never()).requireCompanyUser(any(), any());
    }

    @Test
    void defaultStagesAreCreatedOnFirstUseAndTheLastIsDone() {
        TestLogin.as(30L, "PROJECT_VIEW");
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(30L, EStageKind.BUG)).thenReturn(List.of());
        when(stageRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        List<StageService.StageDto> bugs = stages.list(null, EStageKind.BUG);
        assertThat(bugs).extracting(StageService.StageDto::name)
                .containsExactly("Unconfirmed", "Confirmed", "In Progress", "Resolved", "Verified");
        assertThat(bugs).noneMatch(StageService.StageDto::done);

        TestLogin.as(10L, "PROJECT_VIEW");
        assertThat(stages.list(null, EStageKind.TASK)).extracting(StageService.StageDto::done)
                .containsExactly(false, false, false, true);
    }

    @Test
    void calendarRangeIsChecked() {
        TestLogin.as(10L, "PROJECT_VIEW");
        assertThatThrownBy(() -> taskService.calendar(null, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EProjectError.PRJ_023);
        Task a = task(50L, 104L, 1);
        a.setTitle("Launch");
        a.setDueDate(LocalDate.of(2026, 12, 5));
        when(taskRepo.dueBetween(10L, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of(a));
        when(projectRepo.findByIdIn(any())).thenReturn(List.of(project));
        assertThat(taskService.calendar(null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
                .singleElement().satisfies(i -> {
                    assertThat(i.projectName()).isEqualTo("Website");
                    assertThat(i.stageName()).isEqualTo("Done");
                    assertThat(i.done()).isTrue();
                });
    }

    private static ProjectForm form(LocalDate start, LocalDate end, List<Long> users) {
        return new ProjectForm(null, "Website", start, end, new BigDecimal("1000"), null, users);
    }

    private static TaskService.TaskForm taskForm(List<Long> assignees, Long milestoneId) {
        return new TaskService.TaskForm(milestoneId, "Home page", EPriority.HIGH, null, assignees, null, null, null);
    }

    private static Task task(Long id, Long stageId, int position) {
        Task t = new Task();
        t.setId(id);
        t.setCompanyId(10L);
        t.setProjectId(1L);
        t.setTitle("T" + id);
        t.setPriority(EPriority.MEDIUM);
        t.setStageId(stageId);
        t.setPosition(position);
        return t;
    }
}
