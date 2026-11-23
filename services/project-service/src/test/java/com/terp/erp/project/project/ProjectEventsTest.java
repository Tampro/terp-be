package com.terp.erp.project.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.project.TestLogin;
import com.terp.erp.project.client.AccountingClient;
import com.terp.erp.project.client.IamClient;
import com.terp.erp.project.client.MediaClient;
import com.terp.erp.project.project.ProjectEnums.EMilestoneStatus;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.task.Bug;
import com.terp.erp.project.task.BugRepository;
import com.terp.erp.project.task.BugService;
import com.terp.erp.project.task.Stage;
import com.terp.erp.project.task.StageRepository;
import com.terp.erp.project.task.StageService;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each project-service producer writes its contract event (type, aggregate, payload keys) to the outbox. */
class ProjectEventsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);

    private final ProjectRepository projectRepo = mock(ProjectRepository.class);
    private final MilestoneRepository milestoneRepo = mock(MilestoneRepository.class);
    private final StageRepository stageRepo = mock(StageRepository.class);
    private final TaskRepository taskRepo = mock(TaskRepository.class);
    private final TaskCommentRepository commentRepo = mock(TaskCommentRepository.class);
    private final BugRepository bugRepo = mock(BugRepository.class);
    private final IamClient iam = mock(IamClient.class);
    private final ActivityService activity = mock(ActivityService.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);

    private final StageService stages = new StageService(stageRepo, taskRepo, bugRepo);
    private final ProjectService projects = new ProjectService(projectRepo, taskRepo, stages, iam,
            mock(AccountingClient.class), activity, outbox, CLOCK);
    private final TaskService taskService = new TaskService(projects, projectRepo, milestoneRepo, stages, taskRepo,
            mock(SubtaskRepository.class), commentRepo, activity, outbox);
    private final BugService bugService = new BugService(projects, stages, bugRepo, activity, outbox);
    private final ProjectPartsService parts = new ProjectPartsService(projects, milestoneRepo,
            mock(AttachmentRepository.class), taskRepo, mock(MediaClient.class), activity, outbox);

    private final Project project = new Project();

    @BeforeEach
    void setUp() {
        project.setId(1L);
        project.setCompanyId(10L);
        project.setName("Website");
        project.getUserIds().addAll(Set.of(7L, 8L));
        when(projectRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(project));
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(10L, EStageKind.TASK))
                .thenReturn(stages(EStageKind.TASK, 101L));
        when(stageRepo.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(10L, EStageKind.BUG))
                .thenReturn(stages(EStageKind.BUG, 201L));
        when(taskRepo.save(any())).thenAnswer(i -> { ((Task) i.getArgument(0)).setId(50L); return i.getArgument(0); });
        when(bugRepo.save(any())).thenAnswer(i -> { ((Bug) i.getArgument(0)).setId(70L); return i.getArgument(0); });
        when(milestoneRepo.save(any())).thenAnswer(i -> { ((Milestone) i.getArgument(0)).setId(60L); return i.getArgument(0); });
        when(commentRepo.save(any())).thenAnswer(i -> { ((TaskComment) i.getArgument(0)).setId(80L); return i.getArgument(0); });
        when(projectRepo.save(any())).thenAnswer(i -> { ((Project) i.getArgument(0)).setId(1L); return i.getArgument(0); });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void projectCreated() {
        TestLogin.as(10L, "PROJECT_MANAGE");
        projects.create(new ProjectService.ProjectForm(null, "Website", LocalDate.of(2026, 12, 1), null,
                new BigDecimal("1000"), null, List.of()));
        Map<String, Object> p = captured("project.project.created", "PROJECT", "1");
        assertThat(p).containsKeys("projectId", "title", "status", "startDate", "endDate").containsEntry("title", "Website");
    }

    @Test
    void milestoneCreated() {
        TestLogin.as(10L, "PROJECT_MANAGE");
        parts.addMilestone(1L, new ProjectPartsService.MilestoneForm("Design approved", null, null, null, null,
                EMilestoneStatus.INCOMPLETE, 0));
        Map<String, Object> p = captured("project.milestone.created", "MILESTONE", "60");
        assertThat(p).containsKeys("milestoneId", "title", "projectId", "projectName", "endDate");
    }

    @Test
    void taskCreatedCarriesAssigneesAsRecipients() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        taskService.create(1L, new TaskService.TaskForm(null, "Home page", EPriority.HIGH, null, List.of(7L, 8L),
                null, null, null));
        Map<String, Object> p = captured("project.task.created", "TASK", "50");
        assertThat(p).containsKeys("taskId", "title", "projectId", "projectName", "priority", "dueDate")
                .containsEntry("recipientUserIds", List.of("7", "8"));
    }

    @Test
    void taskMovedOnlyWhenTheStageChanges() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        Task t = new Task();
        t.setId(50L);
        t.setCompanyId(10L);
        t.setProjectId(1L);
        t.setTitle("Home page");
        t.setStageId(101L);
        when(taskRepo.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(t));
        stagesById(EStageKind.TASK, 101L);
        when(taskRepo.findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(eq(1L), any()))
                .thenReturn(new ArrayList<>());
        taskService.move(50L, new TaskService.MoveForm(102L, null));
        Map<String, Object> p = captured("project.task.moved", "TASK", "50");
        assertThat(p).containsKeys("taskId", "title", "projectId", "fromStageId", "toStageId", "toStageName")
                .containsEntry("toStageId", "102");
    }

    @Test
    void taskCommentedLeavesTheBodyOut() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        Task t = new Task();
        t.setId(50L);
        t.setCompanyId(10L);
        t.setProjectId(1L);
        t.setTitle("Home page");
        when(taskRepo.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(t));
        taskService.addComment(50L, new TaskService.CommentForm("secret internal note"));
        Map<String, Object> p = captured("project.task.commented", "TASK", "50");
        assertThat(p).containsKeys("taskId", "title", "projectId", "commentId", "authorUserId")
                .doesNotContainKey("body");
    }

    @Test
    void bugCreatedCarriesTheAssignee() {
        TestLogin.as(10L, "PROJECT_TASK_MANAGE");
        bugService.create(1L, new BugService.BugForm("Login button misaligned", EPriority.HIGH, 7L, null, null));
        Map<String, Object> p = captured("project.bug.created", "BUG", "70");
        assertThat(p).containsKeys("bugId", "title", "projectId", "projectName", "priority")
                .containsEntry("recipientUserIds", List.of("7"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captured(String type, String aggregate, String aggregateId) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq(type), eq(aggregate), eq(Long.valueOf(aggregateId)), payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    private List<Stage> stages(EStageKind kind, long firstId) {
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

    private void stagesById(EStageKind kind, long firstId) {
        for (Stage s : stages(kind, firstId)) {
            when(stageRepo.findByIdAndDeletedFalse(s.getId())).thenReturn(Optional.of(s));
        }
    }
}
