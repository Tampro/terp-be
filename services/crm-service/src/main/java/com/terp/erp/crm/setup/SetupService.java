package com.terp.erp.crm.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmLinkRepository;
import com.terp.erp.crm.record.DealRepository;
import com.terp.erp.crm.record.LeadRepository;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.HashSet;
import java.util.List;

/** CRM setup (ERP manual 7.1): pipelines with ordered lead and deal stages, labels per pipeline, sources. */
@Service
@RequiredArgsConstructor
public class SetupService {
    private final PipelineRepository pipelines;
    private final StageRepository stages;
    private final LabelRepository labels;
    private final SourceRepository sources;
    private final LeadRepository leads;
    private final DealRepository deals;
    private final CrmLinkRepository links;

    public record NameForm(
            @Schema(description = "Name", example = "Marketing", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name) {
    }

    public record StageForm(
            @Schema(description = "LEAD or DEAL", example = "DEAL", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EStageKind kind,
            @Schema(description = "Name", example = "Qualification", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name) {
    }

    public record StageOrderForm(
            @Schema(description = "LEAD or DEAL", example = "LEAD", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EStageKind kind,
            @Schema(description = "Every stage id of that kind in the new order", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty List<@NotNull Long> stageIds) {
    }

    public record LabelForm(
            @Schema(description = "Pipeline id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long pipelineId,
            @Schema(description = "Name", example = "Hot", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Colour #RRGGBB", example = "#EF4444", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color) {
    }

    public record StageDto(String id, String pipelineId, EStageKind kind, String name, int position) {
        static StageDto of(Stage s) {
            return new StageDto(s.getId().toString(), s.getPipelineId().toString(), s.getKind(), s.getName(),
                    s.getPosition());
        }
    }

    public record PipelineDto(String id, String name, List<StageDto> leadStages, List<StageDto> dealStages) {
    }

    public record LabelDto(String id, String pipelineId, String name, String color) {
        static LabelDto of(Label l) {
            return new LabelDto(l.getId().toString(), l.getPipelineId().toString(), l.getName(), l.getColor());
        }
    }

    public record SourceDto(String id, String name) {
        static SourceDto of(Source s) {
            return new SourceDto(s.getId().toString(), s.getName());
        }
    }

    // ---------- pipelines & stages ----------

    @Transactional(readOnly = true)
    public List<PipelineDto> pipelines(Long companyId) {
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        return pipelines.findByCompanyIdAndDeletedFalseOrderByNameAsc(company).stream().map(this::dto).toList();
    }

    @Transactional
    public PipelineDto createPipeline(Long companyId, NameForm f) {
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        if (pipelines.existsByCompanyIdAndNameAndDeletedFalse(company, f.name().trim())) {
            throw ApiException.badRequest(ECrmError.CRM_019, f.name().trim());
        }
        Pipeline p = new Pipeline();
        p.setCompanyId(company);
        p.setName(f.name().trim());
        return dto(pipelines.save(p));
    }

    @Transactional
    public PipelineDto renamePipeline(Long id, NameForm f) {
        Pipeline p = requirePipeline(id);
        p.setName(f.name().trim());
        return dto(p);
    }

    /** Refused while a lead or deal is in it (CRM_009). */
    @Transactional
    public void deletePipeline(Long id) {
        Pipeline p = requirePipeline(id);
        if (leads.existsByPipelineIdAndDeletedFalse(id) || deals.existsByPipelineIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ECrmError.CRM_009, p.getName());
        }
        p.setDeleted(true);
        stages.findByPipelineIdAndDeletedFalseOrderByKindAscPositionAscIdAsc(id).forEach(s -> s.setDeleted(true));
        labels.findByPipelineIdAndDeletedFalseOrderByNameAsc(id).forEach(l -> l.setDeleted(true));
    }

    @Transactional
    public StageDto addStage(Long pipelineId, StageForm f) {
        Pipeline p = requirePipeline(pipelineId);
        Stage s = new Stage();
        s.setCompanyId(p.getCompanyId());
        s.setPipelineId(p.getId());
        s.setKind(f.kind());
        s.setName(f.name().trim());
        s.setPosition(stages.maxPosition(p.getId(), f.kind()) + 1);
        return StageDto.of(stages.save(s));
    }

    @Transactional
    public StageDto renameStage(Long stageId, NameForm f) {
        Stage s = requireStage(stageId);
        s.setName(f.name().trim());
        return StageDto.of(s);
    }

    /** Refused while a lead or deal sits in it (CRM_009). */
    @Transactional
    public void deleteStage(Long stageId) {
        Stage s = requireStage(stageId);
        boolean used = s.getKind() == EStageKind.LEAD ? leads.existsByStageIdAndDeletedFalse(stageId)
                : deals.existsByStageIdAndDeletedFalse(stageId);
        if (used) {
            throw ApiException.badRequest(ECrmError.CRM_009, s.getName());
        }
        s.setDeleted(true);
    }

    /** Drag-and-drop order of the columns: the list must hold exactly the stages of that kind (CRM_020). */
    @Transactional
    public PipelineDto reorder(Long pipelineId, StageOrderForm f) {
        Pipeline p = requirePipeline(pipelineId);
        List<Stage> current = stages.findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(p.getId(), f.kind());
        if (current.size() != f.stageIds().size() || !new HashSet<>(f.stageIds())
                .equals(new HashSet<>(current.stream().map(Stage::getId).toList()))) {
            throw ApiException.badRequest(ECrmError.CRM_020);
        }
        for (Stage s : current) {
            s.setPosition(f.stageIds().indexOf(s.getId()) + 1);
        }
        return dto(p);
    }

    // ---------- labels & sources ----------

    @Transactional(readOnly = true)
    public List<LabelDto> labels(Long companyId, Long pipelineId) {
        if (pipelineId != null) {
            return labels.findByPipelineIdAndDeletedFalseOrderByNameAsc(requirePipeline(pipelineId).getId()).stream()
                    .map(LabelDto::of).toList();
        }
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        return labels.findByCompanyIdAndDeletedFalseOrderByNameAsc(company).stream().map(LabelDto::of).toList();
    }

    @Transactional
    public LabelDto saveLabel(Long id, LabelForm f) {
        Pipeline p = requirePipeline(f.pipelineId());
        Label l = id == null ? new Label() : requireLabel(id);
        l.setCompanyId(p.getCompanyId());
        l.setPipelineId(p.getId());
        l.setName(f.name().trim());
        l.setColor(f.color().toUpperCase());
        return LabelDto.of(labels.save(l));
    }

    @Transactional
    public void deleteLabel(Long id) {
        Label l = requireLabel(id);
        if (links.existsByLinkTypeAndRefId(ELinkType.LABEL, id)) {
            throw ApiException.badRequest(ECrmError.CRM_009, l.getName());
        }
        l.setDeleted(true);
    }

    @Transactional(readOnly = true)
    public List<SourceDto> sources(Long companyId) {
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        return sources.findByCompanyIdAndDeletedFalseOrderByNameAsc(company).stream().map(SourceDto::of).toList();
    }

    @Transactional
    public SourceDto saveSource(Long companyId, Long id, NameForm f) {
        Source s;
        if (id == null) {
            s = new Source();
            s.setCompanyId(TenantGuard.resolveCompany(companyId, ECrmError.CRM_001));
        } else {
            s = requireSource(id);
        }
        s.setName(f.name().trim());
        return SourceDto.of(sources.save(s));
    }

    @Transactional
    public void deleteSource(Long id) {
        Source s = requireSource(id);
        if (links.existsByLinkTypeAndRefId(ELinkType.SOURCE, id)) {
            throw ApiException.badRequest(ECrmError.CRM_009, s.getName());
        }
        s.setDeleted(true);
    }

    // ---------- lookups for leads and deals ----------

    public Pipeline requirePipeline(Long id) {
        Pipeline p = pipelines.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_002));
        TenantGuard.assertSameCompany(p.getCompanyId(), ECrmError.CRM_002);
        return p;
    }

    public Stage requireStage(Long id) {
        Stage s = stages.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_003));
        TenantGuard.assertSameCompany(s.getCompanyId(), ECrmError.CRM_003);
        return s;
    }

    /** A stage of this pipeline and kind (CRM_008 otherwise). */
    public Stage requireStageOf(Long stageId, Long pipelineId, EStageKind kind) {
        Stage s = requireStage(stageId);
        if (!s.getPipelineId().equals(pipelineId) || s.getKind() != kind) {
            throw ApiException.badRequest(ECrmError.CRM_008, kind.name().toLowerCase());
        }
        return s;
    }

    /** The first stage of that kind; a pipeline without one cannot take leads or deals (CRM_010). */
    public Stage firstStage(Long pipelineId, EStageKind kind) {
        return stages.findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(pipelineId, kind).stream()
                .findFirst().orElseThrow(() -> ApiException.badRequest(ECrmError.CRM_010));
    }

    public Label requireLabel(Long id) {
        Label l = labels.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_004));
        TenantGuard.assertSameCompany(l.getCompanyId(), ECrmError.CRM_004);
        return l;
    }

    public Source requireSource(Long id) {
        Source s = sources.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_005));
        TenantGuard.assertSameCompany(s.getCompanyId(), ECrmError.CRM_005);
        return s;
    }

    public List<Stage> stagesOf(Long pipelineId, EStageKind kind) {
        return stages.findByPipelineIdAndKindAndDeletedFalseOrderByPositionAscIdAsc(pipelineId, kind);
    }

    public java.util.Map<Long, String> stageNames(java.util.Collection<Long> ids) {
        java.util.Map<Long, String> m = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            stages.findByIdIn(ids).forEach(s -> m.put(s.getId(), s.getName()));
        }
        return m;
    }

    private PipelineDto dto(Pipeline p) {
        return new PipelineDto(p.getId().toString(), p.getName(),
                stagesOf(p.getId(), EStageKind.LEAD).stream().map(StageDto::of).toList(),
                stagesOf(p.getId(), EStageKind.DEAL).stream().map(StageDto::of).toList());
    }
}
