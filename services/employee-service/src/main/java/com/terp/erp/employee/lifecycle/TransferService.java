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
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transfers page (docs/analysis/06 §7.2.3). The form's to-values form the new placement, an omitted level keeping the
 * employee's current one; the whole branch ⊃ department ⊃ designation chain must hold (EMP_002/003/004/023) and
 * something must change (EMP_048). APPROVED moves the employee at once (PO, 2026-11-20); only a PENDING transfer can
 * be edited, decided or deleted (EMP_046).
 */
@Service
@RequiredArgsConstructor
public class TransferService {
    public enum EChange { BRANCH, DEPARTMENT, DESIGNATION }

    private final EmployeeTransferRepository transfers;
    private final OrgService orgs;
    private final LifecycleSupport support;
    private final Clock clock;

    /** changes = the Transfer Type column; document is only filled on the details. */
    public record TransferDto(String id, EmployeeRef employee, List<EChange> changes, Ref fromBranch,
                              Ref fromDepartment, Ref fromDesignation, Ref toBranch, Ref toDepartment,
                              Ref toDesignation, LocalDate transferDate, LocalDate effectiveDate, String reason,
                              String documentId, FileRef document, EDecision status, Instant decidedAt,
                              Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EDecision, Long> byStatus) {
    }

    public record TransferForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Omitted = the current branch", example = "2") Long toBranchId,
            @Schema(description = "Omitted = the current department", example = "3") Long toDepartmentId,
            @Schema(description = "Omitted = the current designation", example = "4") Long toDesignationId,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate transferDate,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate effectiveDate,
            @Size(max = 2000) String reason,
            @Schema(description = "Media library file id") Long documentMediaId) {
    }

    @Transactional(readOnly = true)
    public Page<TransferDto> search(Long requestedCompanyId, String keyword, Long departmentId, LocalDate from,
                                    LocalDate to, EDecision status, Pageable pageable) {
        Long companyId = LifecycleSupport.company(requestedCompanyId);
        Page<EmployeeTransfer> page = transfers.search(companyId, departmentId, from, to, status,
                DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeTransfer::getEmployeeId).toSet());
        OrgService.Names names = names(companyId, page.getContent());
        return page.map(t -> dto(t, people.get(t.getEmployeeId()), names, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long departmentId, LocalDate from,
                                     LocalDate to) {
        Map<EDecision, Long> by = new EnumMap<>(EDecision.class);
        for (EDecision s : EDecision.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : transfers.countByStatus(LifecycleSupport.company(requestedCompanyId), departmentId, from,
                to, DepartmentService.kw(keyword))) {
            by.put((EDecision) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public TransferDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TransferDto create(TransferForm form) {
        EmployeeTransfer t = new EmployeeTransfer();
        apply(t, form);
        return detail(transfers.save(t));
    }

    /** Full replacement while PENDING. */
    @Transactional
    public TransferDto update(Long id, TransferForm form) {
        EmployeeTransfer t = requirePending(id);
        apply(t, form);
        return detail(t);
    }

    /** Moves the employee to the new placement now; checked again against where they are today. */
    @Transactional
    public TransferDto approve(Long id) {
        EmployeeTransfer t = requirePending(id);
        Employee e = support.employee(t.getEmployeeId());
        placeFrom(t, e);
        check(e, t.getToBranchId(), t.getToDepartmentId(), t.getToDesignationId(), t);
        e.setBranchId(t.getToBranchId());
        e.setDepartmentId(t.getToDepartmentId());
        e.setDesignationId(t.getToDesignationId());
        t.setStatus(EDecision.APPROVED);
        t.setDecidedAt(clock.instant());
        return detail(t);
    }

    @Transactional
    public TransferDto reject(Long id) {
        EmployeeTransfer t = requirePending(id);
        t.setStatus(EDecision.REJECTED);
        t.setDecidedAt(clock.instant());
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        requirePending(id).setDeleted(true);
    }

    private void apply(EmployeeTransfer t, TransferForm f) {
        Employee e = support.employee(f.employeeId());
        if (f.effectiveDate().isBefore(f.transferDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_049, "transfer");
        }
        Long branch = f.toBranchId() != null ? f.toBranchId() : e.getBranchId();
        Long department = f.toDepartmentId() != null ? f.toDepartmentId() : e.getDepartmentId();
        Long designation = f.toDesignationId() != null ? f.toDesignationId() : e.getDesignationId();
        check(e, branch, department, designation, t);
        t.setCompanyId(e.getCompanyId());
        t.setEmployeeId(e.getId());
        placeFrom(t, e);
        t.setToBranchId(branch);
        t.setToDepartmentId(department);
        t.setToDesignationId(designation);
        t.setTransferDate(f.transferDate());
        t.setEffectiveDate(f.effectiveDate());
        t.setReason(AwardService.blankToNull(f.reason()));
        t.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
    }

    /** Chain valid (inactive allowed only where the employee already is or the transfer already pointed) and new. */
    private void check(Employee e, Long branch, Long department, Long designation, EmployeeTransfer t) {
        orgs.assertChain(e.getCompanyId(), branch, department, designation,
                keep(e.getBranchId(), t.getToBranchId(), branch), keep(e.getDepartmentId(), t.getToDepartmentId(),
                        department), keep(e.getDesignationId(), t.getToDesignationId(), designation));
        if (Objects.equals(branch, e.getBranchId()) && Objects.equals(department, e.getDepartmentId())
                && Objects.equals(designation, e.getDesignationId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_048);
        }
    }

    /** The id assertChain may accept although inactive: where the employee is, or the transfer's earlier choice. */
    private static Long keep(Long current, Long earlier, Long chosen) {
        return Objects.equals(chosen, earlier) ? earlier : current;
    }

    private static void placeFrom(EmployeeTransfer t, Employee e) {
        t.setFromBranchId(e.getBranchId());
        t.setFromDepartmentId(e.getDepartmentId());
        t.setFromDesignationId(e.getDesignationId());
    }

    static List<EChange> changes(EmployeeTransfer t) {
        List<EChange> out = new ArrayList<>();
        if (!Objects.equals(t.getFromBranchId(), t.getToBranchId())) {
            out.add(EChange.BRANCH);
        }
        if (!Objects.equals(t.getFromDepartmentId(), t.getToDepartmentId())) {
            out.add(EChange.DEPARTMENT);
        }
        if (!Objects.equals(t.getFromDesignationId(), t.getToDesignationId())) {
            out.add(EChange.DESIGNATION);
        }
        return out;
    }

    private OrgService.Names names(Long companyId, Collection<EmployeeTransfer> list) {
        Set<Long> b = new HashSet<>();
        Set<Long> d = new HashSet<>();
        Set<Long> g = new HashSet<>();
        for (EmployeeTransfer t : list) {
            addIfSet(b, t.getFromBranchId(), t.getToBranchId());
            addIfSet(d, t.getFromDepartmentId(), t.getToDepartmentId());
            addIfSet(g, t.getFromDesignationId(), t.getToDesignationId());
        }
        return orgs.names(companyId, b, d, g);
    }

    private static void addIfSet(Set<Long> into, Long a, Long b) {
        if (a != null) {
            into.add(a);
        }
        if (b != null) {
            into.add(b);
        }
    }

    private TransferDto detail(EmployeeTransfer t) {
        return dto(t, support.refs(List.of(t.getEmployeeId())).get(t.getEmployeeId()),
                names(t.getCompanyId(), List.of(t)), true);
    }

    private TransferDto dto(EmployeeTransfer t, EmployeeRef employee, OrgService.Names n, boolean withFile) {
        return new TransferDto(t.getId().toString(), employee, changes(t), ref(t.getFromBranchId(), n.branches()),
                ref(t.getFromDepartmentId(), n.departments()), ref(t.getFromDesignationId(), n.designations()),
                ref(t.getToBranchId(), n.branches()), ref(t.getToDepartmentId(), n.departments()),
                ref(t.getToDesignationId(), n.designations()), t.getTransferDate(), t.getEffectiveDate(),
                t.getReason(), t.getDocumentMediaId() == null ? null : t.getDocumentMediaId().toString(),
                withFile ? support.fileRef(t.getDocumentMediaId()) : null, t.getStatus(), t.getDecidedAt(),
                t.getCreatedDate(), t.getUpdatedDate());
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id.toString(), names.get(id));
    }

    private EmployeeTransfer requirePending(Long id) {
        EmployeeTransfer t = require(id);
        if (t.getStatus() != EDecision.PENDING) {
            throw ApiException.badRequest(EEmployeeError.EMP_046);
        }
        return t;
    }

    private EmployeeTransfer require(Long id) {
        EmployeeTransfer t = transfers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_045));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_045);
        return t;
    }
}
