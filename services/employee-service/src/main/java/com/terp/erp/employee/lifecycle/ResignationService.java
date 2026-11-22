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
import com.terp.erp.employee.lifecycle.EmployeeResignation.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resignations page (docs/analysis/06 §7.2.5, FR-ERP-CAR-001). PENDING -> ACCEPTED | REJECTED, once (EMP_096).
 * ACCEPTED terminates the employee at once (and locks the login); a decided record is locked (EMP_046) except for
 * its exit interview.
 */
@Service
@RequiredArgsConstructor
public class ResignationService {
    private final EmployeeResignationRepository resignations;
    private final LifecycleSupport support;
    private final EmployeeExit exit;
    private final Clock clock;

    /** document only on the details. */
    public record ResignationDto(String id, EmployeeRef employee, LocalDate resignationDate, LocalDate lastWorkingDay,
                                 String noticePeriod, String reason, String description, String documentId,
                                 FileRef document, EStatus status, ExitInterview exitInterview, Instant completedAt,
                                 Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record ResignationForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate resignationDate,
            @Schema(example = "2026-12-20") LocalDate lastWorkingDay,
            @Schema(example = "30 days") @Size(max = 50) String noticePeriod,
            @Schema(example = "Career change") @Size(max = 500) String reason,
            @Size(max = 5000) String description,
            @Schema(description = "Media library file id") Long documentMediaId) {
    }

    static final Map<EStatus, java.util.Set<EStatus>> MOVES = Map.of(
            EStatus.PENDING, java.util.EnumSet.of(EStatus.ACCEPTED, EStatus.REJECTED));

    public record StatusForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "ACCEPTED (terminates the employee now) or REJECTED; only from PENDING") @NotNull
            EStatus status,
            boolean exitInterviewConducted,
            LocalDate exitInterviewDate,
            @Size(max = 5000) String exitFeedback) {
    }

    /** The exit interview alone; recordable at any status (it usually happens after the decision). */
    public record ExitInterviewForm(
            boolean exitInterviewConducted,
            LocalDate exitInterviewDate,
            @Size(max = 5000) String exitFeedback) {
    }

    @Transactional(readOnly = true)
    public Page<ResignationDto> search(Long requestedCompanyId, String keyword, Long employeeId, LocalDate from,
                                       LocalDate to, EStatus status, Pageable pageable) {
        Page<EmployeeResignation> page = resignations.search(LifecycleSupport.company(requestedCompanyId), employeeId,
                from, to, status, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeResignation::getEmployeeId).toSet());
        return page.map(r -> dto(r, people.get(r.getEmployeeId()), false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId, LocalDate from,
                                     LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : resignations.countByStatus(LifecycleSupport.company(requestedCompanyId), employeeId, from,
                to, DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public ResignationDto get(Long id) {
        return detail(require(id));
    }

    /** Starts as PENDING. */
    @Transactional
    public ResignationDto create(ResignationForm form) {
        EmployeeResignation r = new EmployeeResignation();
        apply(r, form);
        return detail(resignations.save(r));
    }

    /** Full replacement of the form while PENDING; status and exit interview are kept. */
    @Transactional
    public ResignationDto update(Long id, ResignationForm form) {
        EmployeeResignation r = requireOpen(id);
        apply(r, form);
        return detail(r);
    }

    @Transactional
    public ResignationDto changeStatus(Long id, StatusForm f) {
        EmployeeResignation r = require(id);
        LifecycleSupport.assertMove(MOVES, r.getStatus(), f.status());
        EmployeeExit.interview(r, f.exitInterviewConducted(), f.exitInterviewDate(), f.exitFeedback());
        if (f.status() == EStatus.ACCEPTED) {
            exit.terminate(support.employee(r.getEmployeeId()));
            r.setCompletedAt(clock.instant());
        }
        r.setStatus(f.status());
        return detail(r);
    }

    @Transactional
    public ResignationDto exitInterview(Long id, ExitInterviewForm f) {
        EmployeeResignation r = require(id);
        EmployeeExit.interview(r, f.exitInterviewConducted(), f.exitInterviewDate(), f.exitFeedback());
        return detail(r);
    }

    @Transactional
    public void delete(Long id) {
        requireOpen(id).setDeleted(true);
    }

    private void apply(EmployeeResignation r, ResignationForm f) {
        Employee e = support.employee(f.employeeId());
        EmployeeExit.assertEmployed(e);
        if (f.lastWorkingDay() != null && f.lastWorkingDay().isBefore(f.resignationDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "last working day", "resignation date");
        }
        r.setCompanyId(e.getCompanyId());
        r.setEmployeeId(e.getId());
        r.setResignationDate(f.resignationDate());
        r.setLastWorkingDay(f.lastWorkingDay());
        r.setNoticePeriod(AwardService.blankToNull(f.noticePeriod()));
        r.setReason(AwardService.blankToNull(f.reason()));
        r.setDescription(AwardService.blankToNull(f.description()));
        r.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
    }

    private ResignationDto detail(EmployeeResignation r) {
        return dto(r, support.refs(List.of(r.getEmployeeId())).get(r.getEmployeeId()), true);
    }

    private ResignationDto dto(EmployeeResignation r, EmployeeRef employee, boolean withFile) {
        return new ResignationDto(r.getId().toString(), employee, r.getResignationDate(), r.getLastWorkingDay(),
                r.getNoticePeriod(), r.getReason(), r.getDescription(),
                r.getDocumentMediaId() == null ? null : r.getDocumentMediaId().toString(),
                withFile ? support.fileRef(r.getDocumentMediaId()) : null, r.getStatus(), ExitInterview.of(r),
                r.getCompletedAt(), r.getCreatedDate(), r.getUpdatedDate());
    }

    private EmployeeResignation requireOpen(Long id) {
        EmployeeResignation r = require(id);
        EmployeeExit.assertOpen(r.getStatus() != EStatus.PENDING);
        return r;
    }

    private EmployeeResignation require(Long id) {
        EmployeeResignation r = resignations.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_055));
        TenantGuard.assertSameCompany(r.getCompanyId(), EEmployeeError.EMP_055);
        return r;
    }
}
