package com.terp.erp.project.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.project.client.MediaClient;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.project.ProjectEnums.EMilestoneStatus;
import com.terp.erp.project.task.TaskRepository;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Milestones and attachments of a project (detail tabs, FR-PRJ-003). */
@Service
@RequiredArgsConstructor
public class ProjectPartsService {
    private final ProjectService projects;
    private final MilestoneRepository milestones;
    private final AttachmentRepository attachments;
    private final TaskRepository tasks;
    private final MediaClient media;
    private final ActivityService activity;
    private final OutboxPublisher outbox;

    public record MilestoneForm(
            @Schema(description = "Title", example = "Design approved", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 191) String title,
            @Schema(description = "Cost", example = "20000000") @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal cost,
            @Schema(description = "Start date", example = "2026-12-01") LocalDate startDate,
            @Schema(description = "End date, not before the start date", example = "2026-12-31") LocalDate endDate,
            @Schema(description = "Summary") @Size(max = 20000) String summary,
            @Schema(description = "INCOMPLETE, ONGOING or COMPLETE", example = "ONGOING", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EMilestoneStatus status,
            @Schema(description = "Progress 0-100", example = "40") @Min(0) @Max(100) int progress) {
    }

    public record MilestoneDto(String id, String projectId, String title, BigDecimal cost, LocalDate startDate,
                               LocalDate endDate, String summary, EMilestoneStatus status, int progress) {
        static MilestoneDto of(Milestone m) {
            return new MilestoneDto(m.getId() == null ? null : m.getId().toString(), m.getProjectId().toString(),
                    m.getTitle(), m.getCost(), m.getStartDate(), m.getEndDate(), m.getSummary(), m.getStatus(),
                    m.getProgress());
        }
    }

    public record AttachmentForm(
            @Schema(description = "media-service file id of the same company", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long fileId) {
    }

    public record AttachmentDto(String id, String projectId, String fileId, String name, Instant createdDate) {
        static AttachmentDto of(Attachment a) {
            return new AttachmentDto(a.getId() == null ? null : a.getId().toString(), a.getProjectId().toString(),
                    a.getFileId().toString(), a.getName(), a.getCreatedDate());
        }
    }

    // ---------- milestones ----------

    @Transactional(readOnly = true)
    public List<MilestoneDto> milestones(Long projectId) {
        Project p = projects.require(projectId);
        return milestones.findByProjectIdAndDeletedFalseOrderByStartDateAscIdAsc(p.getId()).stream()
                .map(MilestoneDto::of).toList();
    }

    @Transactional
    public MilestoneDto addMilestone(Long projectId, MilestoneForm f) {
        Project p = projects.requireManaged(projectId);
        Milestone m = new Milestone();
        m.setCompanyId(p.getCompanyId());
        m.setProjectId(p.getId());
        apply(m, f);
        milestones.save(m);
        activity.log(p.getCompanyId(), p.getId(), "MILESTONE_CREATED", m.getTitle());
        outbox.publish(p.getCompanyId(), "project.milestone.created", "MILESTONE", m.getId(), Payload.of(
                "milestoneId", m.getId(), "title", m.getTitle(), "projectId", p.getId(), "projectName", p.getName(),
                "endDate", m.getEndDate() == null ? null : m.getEndDate().toString()));
        return MilestoneDto.of(m);
    }

    /** Full replacement. */
    @Transactional
    public MilestoneDto updateMilestone(Long id, MilestoneForm f) {
        Milestone m = requireMilestone(id);
        projects.requireManaged(m.getProjectId());
        apply(m, f);
        activity.log(m.getCompanyId(), m.getProjectId(), "MILESTONE_UPDATED", m.getTitle());
        return MilestoneDto.of(m);
    }

    /** Tasks of the milestone keep existing and lose the link. */
    @Transactional
    public void deleteMilestone(Long id) {
        Milestone m = requireMilestone(id);
        projects.requireManaged(m.getProjectId());
        m.setDeleted(true);
        tasks.findByProjectIdAndDeletedFalseOrderByPositionAscIdAsc(m.getProjectId()).stream()
                .filter(t -> id.equals(t.getMilestoneId())).forEach(t -> t.setMilestoneId(null));
        activity.log(m.getCompanyId(), m.getProjectId(), "MILESTONE_DELETED", m.getTitle());
    }

    /** A milestone of a live project of the caller's company; otherwise 404 (PRJ_010). */
    @Transactional(readOnly = true)
    public Milestone requireMilestone(Long id) {
        Milestone m = milestones.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_010));
        TenantGuard.assertSameCompany(m.getCompanyId(), EProjectError.PRJ_010);
        projects.require(m.getProjectId());
        return m;
    }

    // ---------- attachments ----------

    @Transactional(readOnly = true)
    public List<AttachmentDto> attachments(Long projectId) {
        Project p = projects.require(projectId);
        return attachments.findByProjectIdOrderByIdAsc(p.getId()).stream().map(AttachmentDto::of).toList();
    }

    /** Attaching the same file twice is a no-op answer of the existing row. */
    @Transactional
    public AttachmentDto attach(Long projectId, AttachmentForm f) {
        Project p = projects.requireManaged(projectId);
        if (attachments.existsByProjectIdAndFileId(p.getId(), f.fileId())) {
            return attachments.findByProjectIdOrderByIdAsc(p.getId()).stream()
                    .filter(a -> a.getFileId().equals(f.fileId())).findFirst().map(AttachmentDto::of).orElseThrow();
        }
        MediaClient.MediaFile file = media.requireCompanyFile(f.fileId(), p.getCompanyId(), EProjectError.PRJ_021);
        Attachment a = new Attachment();
        a.setCompanyId(p.getCompanyId());
        a.setProjectId(p.getId());
        a.setFileId(f.fileId());
        a.setName(file.name());
        attachments.save(a);
        activity.log(p.getCompanyId(), p.getId(), "FILE_ATTACHED", a.getName());
        return AttachmentDto.of(a);
    }

    /** Detaches only: the media file itself stays in the media library. */
    @Transactional
    public void detach(Long attachmentId) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound(EProjectError.PRJ_022));
        TenantGuard.assertSameCompany(a.getCompanyId(), EProjectError.PRJ_022);
        Project p = projects.requireManaged(a.getProjectId());
        attachments.delete(a);
        activity.log(p.getCompanyId(), p.getId(), "FILE_DETACHED", a.getName());
    }

    private static void apply(Milestone m, MilestoneForm f) {
        if (f.startDate() != null && f.endDate() != null && f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EProjectError.PRJ_007);
        }
        m.setTitle(f.title().trim());
        m.setCost(f.cost());
        m.setStartDate(f.startDate());
        m.setEndDate(f.endDate());
        m.setSummary(f.summary());
        m.setStatus(f.status());
        m.setProgress(f.progress());
    }
}
