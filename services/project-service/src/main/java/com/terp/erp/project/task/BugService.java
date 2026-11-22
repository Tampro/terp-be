package com.terp.erp.project.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ActivityService;
import com.terp.erp.project.project.Project;
import com.terp.erp.project.project.ProjectEnums.EPriority;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.project.ProjectService;
import com.terp.erp.project.task.StageService.StageDto;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Project bugs (FR-PRJ-005): Kanban by bug stage; the stage is the bug's status. */
@Service
@RequiredArgsConstructor
public class BugService {
    private final ProjectService projects;
    private final StageService stages;
    private final BugRepository bugs;
    private final ActivityService activity;

    public record BugForm(
            @Schema(description = "Title", example = "Login button misaligned", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 191) String title,
            @Schema(description = "LOW, MEDIUM or HIGH (default MEDIUM)", example = "HIGH") EPriority priority,
            @Schema(description = "Assignee: IAM user id on the project team") Long assigneeId,
            @Schema(description = "Bug stage (status); null = first stage") Long stageId,
            @Schema(description = "Description") @Size(max = 20000) String description) {
    }

    public record BugDto(String id, String projectId, String title, EPriority priority, String assigneeId,
                         String stageId, String stageName, int position, String description, Instant createdDate) {
    }

    public record BugColumn(StageDto stage, List<BugDto> bugs) {
    }

    @Transactional
    public List<BugColumn> board(Long projectId) {
        Project p = projects.require(projectId);
        List<Stage> ordered = stages.ordered(p.getCompanyId(), EStageKind.BUG);
        List<StageDto> dtos = StageService.dtos(ordered);
        Map<Long, Stage> byId = byId(ordered);
        Map<Long, List<Bug>> byStage = bugs.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(p.getId()).stream()
                .collect(Collectors.groupingBy(Bug::getStageId));
        List<BugColumn> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            out.add(new BugColumn(dtos.get(i), byStage.getOrDefault(ordered.get(i).getId(), List.of()).stream()
                    .map(b -> dto(b, byId)).toList()));
        }
        return out;
    }

    @Transactional
    public BugDto get(Long id) {
        Bug b = require(id);
        return dto(b, byId(stages.ordered(b.getCompanyId(), EStageKind.BUG)));
    }

    @Transactional
    public BugDto create(Long projectId, BugForm f) {
        Project p = projects.require(projectId);
        Bug b = new Bug();
        b.setCompanyId(p.getCompanyId());
        b.setProjectId(p.getId());
        apply(p, b, f);
        Stage stage = f.stageId() == null ? stages.firstStage(p.getCompanyId(), EStageKind.BUG)
                : stages.require(f.stageId(), EStageKind.BUG, p.getCompanyId());
        b.setStageId(stage.getId());
        b.setPosition(bugs.maxPosition(p.getId(), stage.getId()) + 1);
        bugs.save(b);
        activity.log(p.getCompanyId(), p.getId(), "BUG_CREATED", b.getTitle());
        return dto(b, byId(stages.ordered(p.getCompanyId(), EStageKind.BUG)));
    }

    /** Full replacement of the fields; the stage changes only through move (stageId here is ignored). */
    @Transactional
    public BugDto update(Long id, BugForm f) {
        Bug b = require(id);
        Project p = projects.require(b.getProjectId());
        apply(p, b, f);
        activity.log(p.getCompanyId(), p.getId(), "BUG_UPDATED", b.getTitle());
        return dto(b, byId(stages.ordered(p.getCompanyId(), EStageKind.BUG)));
    }

    @Transactional
    public BugDto move(Long id, TaskService.MoveForm f) {
        Bug b = require(id);
        Stage target = stages.require(f.stageId(), EStageKind.BUG, b.getCompanyId());
        Long from = b.getStageId();
        List<Bug> column = new ArrayList<>(bugs.findByProjectIdAndStageIdAndDeletedFalseOrderByPositionAscIdAsc(
                b.getProjectId(), target.getId()));
        column.removeIf(x -> x.getId().equals(b.getId()));
        int index = f.position() == null ? column.size() : Math.min(f.position(), column.size());
        column.add(index, b);
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition(i + 1);
        }
        b.setStageId(target.getId());
        if (!target.getId().equals(from)) {
            activity.log(b.getCompanyId(), b.getProjectId(), "BUG_MOVED", b.getTitle() + " -> " + target.getName());
        }
        return dto(b, byId(stages.ordered(b.getCompanyId(), EStageKind.BUG)));
    }

    @Transactional
    public void delete(Long id) {
        Bug b = require(id);
        b.setDeleted(true);
        activity.log(b.getCompanyId(), b.getProjectId(), "BUG_DELETED", b.getTitle());
    }

    /** A bug of a live project of the caller's company; otherwise 404 (PRJ_020). */
    @Transactional(readOnly = true)
    public Bug require(Long id) {
        Bug b = bugs.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_020));
        TenantGuard.assertSameCompany(b.getCompanyId(), EProjectError.PRJ_020);
        try {
            projects.require(b.getProjectId());
        } catch (ApiException e) {
            throw ApiException.notFound(EProjectError.PRJ_020);
        }
        return b;
    }

    private static void apply(Project p, Bug b, BugForm f) {
        if (f.assigneeId() != null && !p.getUserIds().contains(f.assigneeId())) {
            throw ApiException.badRequest(EProjectError.PRJ_017, f.assigneeId());
        }
        b.setTitle(f.title().trim());
        b.setPriority(f.priority() == null ? EPriority.MEDIUM : f.priority());
        b.setAssigneeUserId(f.assigneeId());
        b.setDescription(f.description());
    }

    private static Map<Long, Stage> byId(List<Stage> ordered) {
        return ordered.stream().collect(Collectors.toMap(Stage::getId, Function.identity()));
    }

    private static BugDto dto(Bug b, Map<Long, Stage> stagesById) {
        Stage s = stagesById.get(b.getStageId());
        return new BugDto(b.getId() == null ? null : b.getId().toString(), b.getProjectId().toString(), b.getTitle(),
                b.getPriority(), b.getAssigneeUserId() == null ? null : b.getAssigneeUserId().toString(),
                b.getStageId() == null ? null : b.getStageId().toString(), s == null ? null : s.getName(),
                b.getPosition(), b.getDescription(), b.getCreatedDate());
    }
}
