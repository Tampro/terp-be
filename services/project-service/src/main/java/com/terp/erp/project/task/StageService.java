package com.terp.erp.project.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.security.TenantGuard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Task and bug stages per company (ERP manual 11.1). Defaults are created on first use, so a new company needs no
 * seed; the LAST task stage by position is the done stage that progress counts.
 */
@Service
@RequiredArgsConstructor
public class StageService {
    /** Spec: manual 11.1 / FR-PRJ-004, FR-PRJ-005. */
    static final List<String> DEFAULT_TASK_STAGES = List.of("Todo", "In Progress", "Review", "Done");
    static final List<String> DEFAULT_BUG_STAGES = List.of("Unconfirmed", "Confirmed", "In Progress", "Resolved", "Verified");

    private final StageRepository stages;
    private final TaskRepository tasks;
    private final BugRepository bugs;

    public record StageForm(
            @Schema(description = "TASK or BUG", example = "TASK", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EStageKind kind,
            @Schema(description = "Name", example = "QA", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name) {
    }

    public record RenameForm(
            @Schema(description = "Name", example = "Testing", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name) {
    }

    public record StageOrderForm(
            @Schema(description = "TASK or BUG", example = "TASK", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EStageKind kind,
            @Schema(description = "Every stage id of that kind in the new order", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty List<@NotNull Long> stageIds) {
    }

    public record StageDto(String id, EStageKind kind, String name, int position,
                           @Schema(description = "True for the last task stage: tasks there count as done") boolean done) {
    }

    /** Stages of the company in board order, creating the defaults the first time (hence a write transaction). */
    @Transactional
    public List<StageDto> list(Long companyId, EStageKind kind) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        return dtos(ordered(company, kind));
    }

    /** Board order; creates the default stages when the company has none of that kind yet. */
    @Transactional
    public List<Stage> ordered(Long company, EStageKind kind) {
        List<Stage> list = stages.findByCompanyIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(company, kind);
        if (!list.isEmpty()) {
            return list;
        }
        List<String> names = kind == EStageKind.TASK ? DEFAULT_TASK_STAGES : DEFAULT_BUG_STAGES;
        List<Stage> created = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Stage s = new Stage();
            s.setCompanyId(company);
            s.setKind(kind);
            s.setName(names.get(i));
            s.setPosition(i + 1);
            created.add(stages.save(s));
        }
        return created;
    }

    @Transactional
    public Long doneStageId(Long company) {
        List<Stage> list = ordered(company, EStageKind.TASK);
        return list.get(list.size() - 1).getId();
    }

    @Transactional
    public Stage firstStage(Long company, EStageKind kind) {
        return ordered(company, kind).get(0);
    }

    /** A stage of the caller's company and of that board; another company's id answers 404 (PRJ_012). */
    @Transactional(readOnly = true)
    public Stage require(Long id, EStageKind kind, Long company) {
        Stage s = stages.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_012));
        TenantGuard.assertSameCompany(s.getCompanyId(), EProjectError.PRJ_012);
        if (s.getKind() != kind || !s.getCompanyId().equals(company)) {
            throw ApiException.badRequest(EProjectError.PRJ_024);
        }
        return s;
    }

    @Transactional
    public StageDto create(Long companyId, StageForm f) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        List<Stage> current = ordered(company, f.kind());
        Stage s = new Stage();
        s.setCompanyId(company);
        s.setKind(f.kind());
        s.setName(f.name().trim());
        s.setPosition(current.get(current.size() - 1).getPosition() + 1);
        stages.save(s);
        // A new last task stage becomes the done stage: say so in the answer.
        return dto(s, f.kind() == EStageKind.TASK);
    }

    @Transactional
    public StageDto rename(Long id, RenameForm f) {
        Stage s = requireOwn(id);
        s.setName(f.name().trim());
        List<Stage> list = ordered(s.getCompanyId(), s.getKind());
        return dto(s, s.getKind() == EStageKind.TASK && list.get(list.size() - 1).getId().equals(s.getId()));
    }

    /** Refused while tasks or bugs sit in it (PRJ_014) or when it is the only stage left (PRJ_015). */
    @Transactional
    public void delete(Long id) {
        Stage s = requireOwn(id);
        boolean used = s.getKind() == EStageKind.TASK ? tasks.existsByStageIdAndDeletedFalse(id)
                : bugs.existsByStageIdAndDeletedFalse(id);
        if (used) {
            throw ApiException.badRequest(EProjectError.PRJ_014, s.getName());
        }
        if (ordered(s.getCompanyId(), s.getKind()).size() <= 1) {
            throw ApiException.badRequest(EProjectError.PRJ_015);
        }
        s.setDeleted(true);
    }

    /** Drag-and-drop order of the columns: exactly the stages of that kind (PRJ_013). */
    @Transactional
    public List<StageDto> reorder(Long companyId, StageOrderForm f) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        List<Stage> current = ordered(company, f.kind());
        if (current.size() != f.stageIds().size() || !new HashSet<>(f.stageIds())
                .equals(new HashSet<>(current.stream().map(Stage::getId).toList()))) {
            throw ApiException.badRequest(EProjectError.PRJ_013);
        }
        for (Stage s : current) {
            s.setPosition(f.stageIds().indexOf(s.getId()) + 1);
        }
        List<Stage> sorted = new ArrayList<>(current);
        sorted.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        return dtos(sorted);
    }

    private Stage requireOwn(Long id) {
        Stage s = stages.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_012));
        TenantGuard.assertSameCompany(s.getCompanyId(), EProjectError.PRJ_012);
        return s;
    }

    static List<StageDto> dtos(List<Stage> ordered) {
        List<StageDto> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Stage s = ordered.get(i);
            out.add(dto(s, s.getKind() == EStageKind.TASK && i == ordered.size() - 1));
        }
        return out;
    }

    static StageDto dto(Stage s, boolean done) {
        return new StageDto(s.getId() == null ? null : s.getId().toString(), s.getKind(), s.getName(),
                s.getPosition(), done);
    }
}
