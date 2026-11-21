package com.terp.erp.employee.lifecycle;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeWarning.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Warnings page (docs/analysis/06 §7.2.4). Type and severity come from the company's lists; the warning is issued by
 * another employee of the company (EMP_053). The status moves freely (Change Status); acknowledging records the date
 * (today when omitted) and the employee's response. A warning past its expiry date is only flagged while Issued /
 * Acknowledged, and a nightly job sets EXPIRED (PO 2026-11-21).
 */
@Service
@RequiredArgsConstructor
public class WarningService {
    /** TODO(assumption): "today" is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<EStatus> IN_FORCE = EnumSet.of(EStatus.ISSUED, EStatus.ACKNOWLEDGED);

    private final EmployeeWarningRepository warnings;
    private final LifecycleCatalogService catalogs;
    private final LifecycleCatalogRepository catalogItems;
    private final LifecycleSupport support;
    private final Clock clock;

    /** Nightly: warnings in force past their expiry date (Vietnam date) become EXPIRED. */
    @org.springframework.transaction.annotation.Transactional
    public int expireOverdue() {
        return warnings.expireOverdue(java.time.LocalDate.now(clock.withZone(ZONE)), IN_FORCE, clock.instant());
    }

    public record ImprovementPlan(boolean hasImprovementPlan, String goals, LocalDate startDate, LocalDate endDate,
                                  String progress) {
    }

    /** expired = past the expiry date while Issued / Acknowledged; document only on the details. */
    public record WarningDto(String id, EmployeeRef employee, EmployeeRef warnedBy, Ref warningType, Ref severity,
                             String subject, LocalDate warningDate, String description, String documentId,
                             FileRef document, LocalDate expiryDate, boolean expired, EStatus status,
                             LocalDate acknowledgmentDate, String employeeResponse, ImprovementPlan improvementPlan,
                             Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record WarningForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Warning By: the manager issuing it (employee id)", example = "2") Long warnedById,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warningTypeId,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long severityId,
            @Schema(example = "Repeated late arrival", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String subject,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate warningDate,
            @Size(max = 5000) String description,
            @Schema(description = "Media library file id") Long documentMediaId,
            @Schema(example = "2027-05-20") LocalDate expiryDate,
            boolean hasImprovementPlan,
            @Size(max = 5000) String improvementPlanGoals,
            LocalDate improvementPlanStartDate,
            LocalDate improvementPlanEndDate) {
    }

    public record StatusForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status,
            @Schema(description = "With ACKNOWLEDGED; omitted = today") LocalDate acknowledgmentDate,
            @Size(max = 5000) String employeeResponse) {
    }

    public record PlanForm(
            boolean hasImprovementPlan,
            @Size(max = 5000) String goals,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 5000) String progress) {
    }

    @Transactional(readOnly = true)
    public Page<WarningDto> search(Long requestedCompanyId, String keyword, Long employeeId, Long typeId,
                                   Long severityId, EStatus status, Pageable pageable) {
        Page<EmployeeWarning> page = warnings.search(LifecycleSupport.company(requestedCompanyId), employeeId, typeId,
                severityId, status, DepartmentService.kw(keyword), pageable);
        Set<Long> people = new HashSet<>();
        Set<Long> lists = new HashSet<>();
        for (EmployeeWarning w : page) {
            people.add(w.getEmployeeId());
            if (w.getWarnedById() != null) {
                people.add(w.getWarnedById());
            }
            lists.add(w.getWarningTypeId());
            lists.add(w.getSeverityId());
        }
        Map<Long, EmployeeRef> refs = support.refs(people);
        Map<Long, String> names = names(lists);
        LocalDate today = today();
        return page.map(w -> dto(w, refs, names, today, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId, Long typeId,
                                     Long severityId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : warnings.countByStatus(LifecycleSupport.company(requestedCompanyId), employeeId, typeId,
                severityId, DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public WarningDto get(Long id) {
        return detail(require(id));
    }

    /** Starts as DRAFT. */
    @Transactional
    public WarningDto create(WarningForm form) {
        EmployeeWarning w = new EmployeeWarning();
        apply(w, form);
        return detail(warnings.save(w));
    }

    /** Full replacement of the form; status, acknowledgment and plan progress are kept. */
    @Transactional
    public WarningDto update(Long id, WarningForm form) {
        EmployeeWarning w = require(id);
        apply(w, form);
        return detail(w);
    }

    @Transactional
    public WarningDto changeStatus(Long id, StatusForm f) {
        EmployeeWarning w = require(id);
        LocalDate ack = f.acknowledgmentDate();
        if (f.status() == EStatus.ACKNOWLEDGED && ack == null) {
            ack = today();
        }
        if (ack != null && ack.isBefore(w.getWarningDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "acknowledgment date", "warning date");
        }
        w.setStatus(f.status());
        w.setAcknowledgmentDate(ack);
        w.setEmployeeResponse(AwardService.blankToNull(f.employeeResponse()));
        return detail(w);
    }

    /** The Improvement Plan modal: replaces the plan; turning it off clears it. */
    @Transactional
    public WarningDto updatePlan(Long id, PlanForm f) {
        EmployeeWarning w = require(id);
        plan(w, f.hasImprovementPlan(), f.goals(), f.startDate(), f.endDate());
        w.setImprovementPlanProgress(f.hasImprovementPlan() ? AwardService.blankToNull(f.progress()) : null);
        return detail(w);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(EmployeeWarning w, WarningForm f) {
        Employee e = support.employee(f.employeeId());
        if (f.warnedById() != null) {
            if (f.warnedById().equals(e.getId())) {
                throw ApiException.badRequest(EEmployeeError.EMP_053);
            }
            Employee by = support.employee(f.warnedById());
            if (!by.getCompanyId().equals(e.getCompanyId())) {
                throw ApiException.notFound(EEmployeeError.EMP_001);
            }
        }
        if (f.expiryDate() != null && f.expiryDate().isBefore(f.warningDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "expiry date", "warning date");
        }
        Long typeId = catalogs.usable(ELifecycleCatalogKind.WARNING_TYPE, f.warningTypeId(), e.getCompanyId(),
                w.getWarningTypeId()).getId();
        Long severityId = catalogs.usable(ELifecycleCatalogKind.WARNING_SEVERITY, f.severityId(), e.getCompanyId(),
                w.getSeverityId()).getId();
        w.setCompanyId(e.getCompanyId());
        w.setEmployeeId(e.getId());
        w.setWarnedById(f.warnedById());
        w.setWarningTypeId(typeId);
        w.setSeverityId(severityId);
        w.setSubject(f.subject().trim());
        w.setWarningDate(f.warningDate());
        w.setDescription(AwardService.blankToNull(f.description()));
        w.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
        w.setExpiryDate(f.expiryDate());
        plan(w, f.hasImprovementPlan(), f.improvementPlanGoals(), f.improvementPlanStartDate(),
                f.improvementPlanEndDate());
        if (!f.hasImprovementPlan()) {
            w.setImprovementPlanProgress(null);
        }
    }

    private static void plan(EmployeeWarning w, boolean has, String goals, LocalDate start, LocalDate end) {
        if (has && start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "improvement plan end date", "start date");
        }
        w.setHasImprovementPlan(has);
        w.setImprovementPlanGoals(has ? AwardService.blankToNull(goals) : null);
        w.setImprovementPlanStart(has ? start : null);
        w.setImprovementPlanEnd(has ? end : null);
    }

    private Map<Long, String> names(Set<Long> ids) {
        return catalogItems.findAllById(ids).stream()
                .collect(Collectors.toMap(LifecycleCatalog::getId, LifecycleCatalog::getName));
    }

    private WarningDto detail(EmployeeWarning w) {
        Set<Long> people = new HashSet<>(List.of(w.getEmployeeId()));
        if (w.getWarnedById() != null) {
            people.add(w.getWarnedById());
        }
        return dto(w, support.refs(people), names(Set.of(w.getWarningTypeId(), w.getSeverityId())), today(), true);
    }

    static boolean expired(EmployeeWarning w, LocalDate today) {
        return w.getExpiryDate() != null && w.getExpiryDate().isBefore(today) && IN_FORCE.contains(w.getStatus());
    }

    private WarningDto dto(EmployeeWarning w, Map<Long, EmployeeRef> refs, Map<Long, String> names, LocalDate today,
                           boolean withFile) {
        return new WarningDto(w.getId().toString(), refs.get(w.getEmployeeId()),
                w.getWarnedById() == null ? null : refs.get(w.getWarnedById()),
                new Ref(w.getWarningTypeId().toString(), names.get(w.getWarningTypeId())),
                new Ref(w.getSeverityId().toString(), names.get(w.getSeverityId())), w.getSubject(),
                w.getWarningDate(), w.getDescription(),
                w.getDocumentMediaId() == null ? null : w.getDocumentMediaId().toString(),
                withFile ? support.fileRef(w.getDocumentMediaId()) : null, w.getExpiryDate(), expired(w, today),
                w.getStatus(), w.getAcknowledgmentDate(), w.getEmployeeResponse(),
                new ImprovementPlan(w.isHasImprovementPlan(), w.getImprovementPlanGoals(), w.getImprovementPlanStart(),
                        w.getImprovementPlanEnd(), w.getImprovementPlanProgress()),
                w.getCreatedDate(), w.getUpdatedDate());
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private EmployeeWarning require(Long id) {
        EmployeeWarning w = warnings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_051));
        TenantGuard.assertSameCompany(w.getCompanyId(), EEmployeeError.EMP_051);
        return w;
    }
}
