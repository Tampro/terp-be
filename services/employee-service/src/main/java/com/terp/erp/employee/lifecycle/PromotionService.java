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
import com.terp.erp.employee.org.Designation;
import com.terp.erp.employee.org.DesignationRepository;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Promotions page (docs/analysis/06 §7.2.2, FR-ERP-REC-001). The new placement is newBranchId -> newDepartmentId ->
 * newDesignationId, an omitted branch / department keeping the employee's current one; the chain must hold like a
 * transfer's (EMP_002/003/004/023, inactive EMP_029/030/031) and the designation must change (EMP_098). APPROVED
 * moves the employee's branch, department and designation at once, whatever the effective date (PO, 2026-11-20); a
 * decided promotion is locked (EMP_046).
 */
@Service
@RequiredArgsConstructor
public class PromotionService {
    private final EmployeePromotionRepository promotions;
    private final DesignationRepository designations;
    private final com.terp.erp.employee.org.OrgService orgs;
    private final LifecycleSupport support;
    private final Clock clock;

    /** document is only filled on the details. */
    public record PromotionDto(String id, EmployeeRef employee, String previousDesignation, Ref newBranch,
                               Ref newDepartment, Ref newDesignation,
                               LocalDate promotionDate, LocalDate effectiveDate, String reason, String documentId,
                               FileRef document, EDecision status, Instant decidedAt, Instant createdDate,
                               Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EDecision, Long> byStatus) {
    }

    public record PromotionForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Omitted = the current designation's name", example = "Developer") @Size(max = 150)
            String previousDesignation,
            @Schema(description = "Omitted = the current branch", example = "1") Long newBranchId,
            @Schema(description = "Omitted = the current department; must belong to the (new) branch", example = "3")
            Long newDepartmentId,
            @Schema(description = "Must belong to the (new) department", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long newDesignationId,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate promotionDate,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate effectiveDate,
            @Schema(example = "Exceptional performance and leadership skills") @Size(max = 2000) String reason,
            @Schema(description = "Media library file id") Long documentMediaId,
            @Schema(description = "Initial status on create (APPROVED applies it at once); ignored on edit")
            EDecision status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EDecision status) {
    }

    @Transactional(readOnly = true)
    public Page<PromotionDto> search(Long requestedCompanyId, String keyword, Long designationId, LocalDate from,
                                     LocalDate to, EDecision status, Pageable pageable) {
        Page<EmployeePromotion> page = promotions.search(LifecycleSupport.company(requestedCompanyId), designationId,
                from, to, status, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeePromotion::getEmployeeId).toSet());
        OrgService.Names names = names(page.getContent());
        return page.map(p -> dto(p, people.get(p.getEmployeeId()), names, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long designationId, LocalDate from,
                                     LocalDate to) {
        Map<EDecision, Long> by = new EnumMap<>(EDecision.class);
        for (EDecision s : EDecision.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : promotions.countByStatus(LifecycleSupport.company(requestedCompanyId), designationId, from,
                to, DepartmentService.kw(keyword))) {
            by.put((EDecision) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public PromotionDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public PromotionDto create(PromotionForm form) {
        EmployeePromotion p = new EmployeePromotion();
        Employee e = apply(p, form);
        promotions.save(p);
        if (form.status() != null && form.status() != EDecision.PENDING) {
            decide(p, e, form.status());
        }
        return detail(p);
    }

    /** Full replacement while PENDING; the status is changed with PUT /{id}/status. */
    @Transactional
    public PromotionDto update(Long id, PromotionForm form) {
        EmployeePromotion p = requirePending(id);
        apply(p, form);
        return detail(p);
    }

    /** Update Status: APPROVED changes the employee's designation now. */
    @Transactional
    public PromotionDto changeStatus(Long id, EDecision status) {
        EmployeePromotion p = requirePending(id);
        if (status != EDecision.PENDING) {
            decide(p, support.employee(p.getEmployeeId()), status);
        }
        return detail(p);
    }

    @Transactional
    public void delete(Long id) {
        requirePending(id).setDeleted(true);
    }

    private Employee apply(EmployeePromotion p, PromotionForm f) {
        Employee e = support.employee(f.employeeId());
        if (f.effectiveDate().isBefore(f.promotionDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_049, "promotion");
        }
        Long branch = f.newBranchId() != null ? f.newBranchId() : e.getBranchId();
        Long department = f.newDepartmentId() != null ? f.newDepartmentId()
                : Objects.equals(branch, e.getBranchId()) ? e.getDepartmentId() : null;
        check(e, branch, department, f.newDesignationId(), p);
        p.setCompanyId(e.getCompanyId());
        p.setEmployeeId(e.getId());
        p.setPreviousBranchId(e.getBranchId());
        p.setPreviousDepartmentId(e.getDepartmentId());
        p.setPreviousDesignationId(e.getDesignationId());
        String typed = AwardService.blankToNull(f.previousDesignation());
        p.setPreviousDesignation(typed != null ? typed : e.getDesignationId() == null ? null
                : designations.findById(e.getDesignationId()).map(Designation::getName).orElse(null));
        p.setNewBranchId(branch);
        p.setNewDepartmentId(department);
        p.setNewDesignationId(f.newDesignationId());
        p.setPromotionDate(f.promotionDate());
        p.setEffectiveDate(f.effectiveDate());
        p.setReason(AwardService.blankToNull(f.reason()));
        p.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
        return e;
    }

    /**
     * branch ⊃ department ⊃ designation of the company, with a designation other than the current one. Inactive
     * entries are accepted only where the employee already is or where this promotion already pointed.
     */
    private void check(Employee e, Long branch, Long department, Long designation, EmployeePromotion p) {
        if (branch == null || department == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_023);
        }
        orgs.assertChain(e.getCompanyId(), branch, department, designation,
                keep(e.getBranchId(), p.getNewBranchId(), branch),
                keep(e.getDepartmentId(), p.getNewDepartmentId(), department),
                keep(e.getDesignationId(), p.getNewDesignationId(), designation));
        if (Objects.equals(designation, e.getDesignationId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_098);
        }
    }

    private static Long keep(Long current, Long earlier, Long chosen) {
        return Objects.equals(chosen, earlier) ? earlier : current;
    }

    /**
     * The employee may have moved since: the chain is checked again before the placement changes. Old rows without a
     * new branch / department keep the employee's current ones.
     */
    private void decide(EmployeePromotion p, Employee e, EDecision status) {
        if (status == EDecision.APPROVED) {
            Long branch = p.getNewBranchId() != null ? p.getNewBranchId() : e.getBranchId();
            Long department = p.getNewDepartmentId() != null ? p.getNewDepartmentId() : e.getDepartmentId();
            check(e, branch, department, p.getNewDesignationId(), p);
            e.setBranchId(branch);
            e.setDepartmentId(department);
            e.setDesignationId(p.getNewDesignationId());
        }
        p.setStatus(status);
        p.setDecidedAt(clock.instant());
    }

    private OrgService.Names names(java.util.Collection<EmployeePromotion> rows) {
        java.util.Set<Long> b = new java.util.HashSet<>();
        java.util.Set<Long> d = new java.util.HashSet<>();
        java.util.Set<Long> g = new java.util.HashSet<>();
        Long companyId = null;
        for (EmployeePromotion p : rows) {
            companyId = p.getCompanyId();
            if (p.getNewBranchId() != null) {
                b.add(p.getNewBranchId());
            }
            if (p.getNewDepartmentId() != null) {
                d.add(p.getNewDepartmentId());
            }
            g.add(p.getNewDesignationId());
        }
        return companyId == null ? new OrgService.Names(Map.of(), Map.of(), Map.of())
                : orgs.names(companyId, b, d, g);
    }

    private PromotionDto detail(EmployeePromotion p) {
        return dto(p, support.refs(List.of(p.getEmployeeId())).get(p.getEmployeeId()), names(List.of(p)), true);
    }

    private static Ref ref(Long id, Map<Long, String> names) {
        return id == null ? null : new Ref(id.toString(), names.get(id));
    }

    private PromotionDto dto(EmployeePromotion p, EmployeeRef employee, OrgService.Names n, boolean withFile) {
        return new PromotionDto(p.getId().toString(), employee, p.getPreviousDesignation(),
                ref(p.getNewBranchId(), n.branches()), ref(p.getNewDepartmentId(), n.departments()),
                ref(p.getNewDesignationId(), n.designations()), p.getPromotionDate(), p.getEffectiveDate(),
                p.getReason(), p.getDocumentMediaId() == null ? null : p.getDocumentMediaId().toString(),
                withFile ? support.fileRef(p.getDocumentMediaId()) : null, p.getStatus(), p.getDecidedAt(),
                p.getCreatedDate(), p.getUpdatedDate());
    }

    private EmployeePromotion requirePending(Long id) {
        EmployeePromotion p = require(id);
        if (p.getStatus() != EDecision.PENDING) {
            throw ApiException.badRequest(EEmployeeError.EMP_046);
        }
        return p;
    }

    private EmployeePromotion require(Long id) {
        EmployeePromotion p = promotions.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_044));
        TenantGuard.assertSameCompany(p.getCompanyId(), EEmployeeError.EMP_044);
        return p;
    }
}
