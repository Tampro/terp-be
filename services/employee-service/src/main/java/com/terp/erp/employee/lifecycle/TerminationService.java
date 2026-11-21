package com.terp.erp.employee.lifecycle;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeExit.ExitInterview;
import com.terp.erp.employee.lifecycle.EmployeeTermination.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Terminations page (docs/analysis/06 §7.2.6). Type from the company's termination-types list. The status moves
 * freely until COMPLETED, which terminates the employee at once (and locks the login) and locks the record
 * (PO, 2026-11-20).
 */
@Service
@RequiredArgsConstructor
public class TerminationService {
    private final EmployeeTerminationRepository terminations;
    private final LifecycleCatalogService catalogs;
    private final LifecycleCatalogRepository catalogItems;
    private final LifecycleSupport support;
    private final EmployeeExit exit;
    private final Clock clock;

    /** document only on the details. */
    public record TerminationDto(String id, EmployeeRef employee, Ref terminationType, LocalDate noticeDate,
                                 LocalDate terminationDate, String noticePeriod, String reason, String description,
                                 String documentId, FileRef document, EStatus status, ExitInterview exitInterview,
                                 Instant completedAt, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record TerminationForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Entry of /catalogs/termination-types", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long terminationTypeId,
            @Schema(example = "2026-11-20") LocalDate noticeDate,
            @Schema(example = "2026-12-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate terminationDate,
            @Schema(example = "30 days") @Size(max = 50) String noticePeriod,
            @Schema(example = "Restructuring") @Size(max = 500) String reason,
            @Size(max = 5000) String description,
            @Schema(description = "Media library file id") Long documentMediaId) {
    }

    public record StatusForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "COMPLETED terminates the employee now and locks the record") @NotNull
            EStatus status,
            boolean exitInterviewConducted,
            LocalDate exitInterviewDate,
            @Size(max = 5000) String exitFeedback) {
    }

    @Transactional(readOnly = true)
    public Page<TerminationDto> search(Long requestedCompanyId, String keyword, Long typeId, LocalDate from,
                                       LocalDate to, EStatus status, Pageable pageable) {
        Page<EmployeeTermination> page = terminations.search(LifecycleSupport.company(requestedCompanyId), typeId,
                from, to, status, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeTermination::getEmployeeId).toSet());
        Map<Long, String> types = names(page.map(EmployeeTermination::getTerminationTypeId).toList());
        return page.map(t -> dto(t, people.get(t.getEmployeeId()), types, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long typeId, LocalDate from,
                                     LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : terminations.countByStatus(LifecycleSupport.company(requestedCompanyId), typeId, from, to,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public TerminationDto get(Long id) {
        return detail(require(id));
    }

    /** Starts as PLANNED. */
    @Transactional
    public TerminationDto create(TerminationForm form) {
        EmployeeTermination t = new EmployeeTermination();
        apply(t, form);
        return detail(terminations.save(t));
    }

    /** Full replacement of the form until completed; status and exit interview are kept. */
    @Transactional
    public TerminationDto update(Long id, TerminationForm form) {
        EmployeeTermination t = requireOpen(id);
        apply(t, form);
        return detail(t);
    }

    @Transactional
    public TerminationDto changeStatus(Long id, StatusForm f) {
        EmployeeTermination t = requireOpen(id);
        EmployeeExit.interview(t, f.exitInterviewConducted(), f.exitInterviewDate(), f.exitFeedback());
        if (f.status() == EStatus.COMPLETED) {
            exit.terminate(support.employee(t.getEmployeeId()));
            t.setCompletedAt(clock.instant());
        }
        t.setStatus(f.status());
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        requireOpen(id).setDeleted(true);
    }

    private void apply(EmployeeTermination t, TerminationForm f) {
        Employee e = support.employee(f.employeeId());
        EmployeeExit.assertEmployed(e);
        if (f.noticeDate() != null && f.terminationDate().isBefore(f.noticeDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "termination date", "notice date");
        }
        Long typeId = catalogs.usable(ELifecycleCatalogKind.TERMINATION_TYPE, f.terminationTypeId(), e.getCompanyId(),
                t.getTerminationTypeId()).getId();
        t.setCompanyId(e.getCompanyId());
        t.setEmployeeId(e.getId());
        t.setTerminationTypeId(typeId);
        t.setNoticeDate(f.noticeDate());
        t.setTerminationDate(f.terminationDate());
        t.setNoticePeriod(AwardService.blankToNull(f.noticePeriod()));
        t.setReason(AwardService.blankToNull(f.reason()));
        t.setDescription(AwardService.blankToNull(f.description()));
        t.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
    }

    private Map<Long, String> names(List<Long> ids) {
        return catalogItems.findAllById(ids).stream()
                .collect(Collectors.toMap(LifecycleCatalog::getId, LifecycleCatalog::getName));
    }

    private TerminationDto detail(EmployeeTermination t) {
        return dto(t, support.refs(List.of(t.getEmployeeId())).get(t.getEmployeeId()),
                names(List.of(t.getTerminationTypeId())), true);
    }

    private TerminationDto dto(EmployeeTermination t, EmployeeRef employee, Map<Long, String> types,
                               boolean withFile) {
        return new TerminationDto(t.getId().toString(), employee,
                new Ref(t.getTerminationTypeId().toString(), types.get(t.getTerminationTypeId())), t.getNoticeDate(),
                t.getTerminationDate(), t.getNoticePeriod(), t.getReason(), t.getDescription(),
                t.getDocumentMediaId() == null ? null : t.getDocumentMediaId().toString(),
                withFile ? support.fileRef(t.getDocumentMediaId()) : null, t.getStatus(), ExitInterview.of(t),
                t.getCompletedAt(), t.getCreatedDate(), t.getUpdatedDate());
    }

    private EmployeeTermination requireOpen(Long id) {
        EmployeeTermination t = require(id);
        EmployeeExit.assertOpen(t.getStatus() == EStatus.COMPLETED);
        return t;
    }

    private EmployeeTermination require(Long id) {
        EmployeeTermination t = terminations.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_056));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_056);
        return t;
    }
}
