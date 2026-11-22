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
import com.terp.erp.employee.lifecycle.EmployeeComplaint.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complaints page (docs/analysis/06 §7.2.8, FR-ERP-ISS-001). Statuses and allowed moves (else 400 EMP_096):
 * <pre>
 *   PENDING     -> IN_REVIEW (status), ASSIGNED (assign)
 *   IN_REVIEW   -> ASSIGNED (assign), RESOLVED (resolve)
 *   ASSIGNED    -> IN_PROGRESS (status), ASSIGNED (re-assign), RESOLVED (resolve)
 *   IN_PROGRESS -> ASSIGNED (re-assign), RESOLVED (resolve)
 *   RESOLVED    -> RESOLVED (resolve again = correct the resolution); nothing else
 * </pre>
 * Assign sets the handler, the deadline and ASSIGNED; Resolve records the resolution and sets RESOLVED; Follow-up
 * only after it is resolved (EMP_070). An anonymous complaint hides its complainant from anyone without
 * LIFECYCLE_MANAGE. TODO(assumption): the move table is ours (the manual lists only the statuses) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {
    static final String MANAGE = "LIFECYCLE_MANAGE";
    static final Map<EStatus, Set<EStatus>> MOVES = Map.of(
            EStatus.PENDING, java.util.EnumSet.of(EStatus.IN_REVIEW, EStatus.ASSIGNED),
            EStatus.IN_REVIEW, java.util.EnumSet.of(EStatus.ASSIGNED, EStatus.RESOLVED),
            EStatus.ASSIGNED, java.util.EnumSet.of(EStatus.IN_PROGRESS, EStatus.ASSIGNED, EStatus.RESOLVED),
            EStatus.IN_PROGRESS, java.util.EnumSet.of(EStatus.ASSIGNED, EStatus.RESOLVED),
            EStatus.RESOLVED, java.util.EnumSet.of(EStatus.RESOLVED));
    /** Targets Change Status may set by itself; ASSIGNED and RESOLVED need their own forms. */
    static final Set<EStatus> PLAIN = java.util.EnumSet.of(EStatus.IN_REVIEW, EStatus.IN_PROGRESS);

    private final EmployeeComplaintRepository complaints;
    private final LifecycleCatalogService catalogs;
    private final LifecycleCatalogRepository catalogItems;
    private final LifecycleSupport support;

    public record Assignment(EmployeeRef assignedTo, LocalDate resolutionDeadline) {
    }

    public record Resolution(Ref resolutionType, String investigationNotes, String resolutionAction,
                             LocalDate resolutionDate) {
    }

    public record FollowUp(String action, LocalDate date, String feedback) {
    }

    /** complainant = null when anonymous and the caller lacks LIFECYCLE_MANAGE; document only on the details. */
    public record ComplaintDto(String id, EmployeeRef complainant, boolean anonymous, EmployeeRef against,
                               Ref complaintType, String subject, LocalDate complaintDate, String description,
                               String documentId, FileRef document, EStatus status, Assignment assignment,
                               Resolution resolution, FollowUp followUp, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record ComplaintForm(
            @Schema(description = "Employee filing it", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long complainantId,
            @Schema(description = "Employee complained against; omitted = not a person", example = "2")
            Long againstId,
            @Schema(description = "Entry of /catalogs/complaint-types", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long complaintTypeId,
            @Schema(example = "Inappropriate comments", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String subject,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate complaintDate,
            @Size(max = 5000) String description,
            @Schema(description = "Media library file id") Long documentMediaId,
            @Schema(description = "Submit Anonymously") boolean anonymous) {
    }

    public record StatusForm(
            @Schema(description = "IN_REVIEW (from PENDING) or IN_PROGRESS (from ASSIGNED); use assign / resolve "
                    + "for the others", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record AssignForm(
            @Schema(description = "Employee handling it", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long assignedToId,
            @Schema(example = "2026-12-05") LocalDate resolutionDeadline) {
    }

    public record ResolveForm(
            @Schema(description = "Entry of /catalogs/resolution-types", example = "5",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long resolutionTypeId,
            @Size(max = 5000) String investigationNotes,
            @Size(max = 5000) String resolutionAction,
            @Schema(example = "2026-11-28", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate resolutionDate,
            @Size(max = 5000) String followUpAction,
            LocalDate followUpDate) {
    }

    public record FollowUpForm(
            @Size(max = 5000) String followUpAction,
            LocalDate followUpDate,
            @Schema(description = "Feedback from the complainant") @Size(max = 5000) String feedback) {
    }

    @Transactional(readOnly = true)
    public Page<ComplaintDto> search(Long requestedCompanyId, String keyword, Long typeId, LocalDate from,
                                     LocalDate to, EStatus status, Pageable pageable) {
        Page<EmployeeComplaint> page = complaints.search(LifecycleSupport.company(requestedCompanyId), typeId, from,
                to, status, DepartmentService.kw(keyword), pageable);
        Set<Long> people = new HashSet<>();
        Set<Long> lists = new HashSet<>();
        page.forEach(c -> collect(c, people, lists));
        Map<Long, EmployeeRef> refs = support.refs(people);
        Map<Long, String> names = names(lists);
        boolean manager = canSeeAnonymous();
        return page.map(c -> dto(c, refs, names, manager, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long typeId, LocalDate from,
                                     LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : complaints.countByStatus(LifecycleSupport.company(requestedCompanyId), typeId, from, to,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public ComplaintDto get(Long id) {
        return detail(require(id));
    }

    /** Starts as PENDING. */
    @Transactional
    public ComplaintDto create(ComplaintForm form) {
        EmployeeComplaint c = new EmployeeComplaint();
        apply(c, form);
        return detail(complaints.save(c));
    }

    /**
     * Full replacement of the form while PENDING (EMP_102 afterwards); status, assignment, resolution and follow-up
     * are kept. TODO(assumption): once under review the complainant's text is evidence and is frozen — confirm with BA.
     */
    @Transactional
    public ComplaintDto update(Long id, ComplaintForm form) {
        EmployeeComplaint c = requirePending(id);
        apply(c, form);
        if (c.getAssignedToId() != null && c.getAssignedToId().equals(c.getAgainstId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_069);
        }
        return detail(c);
    }

    @Transactional
    public ComplaintDto changeStatus(Long id, EStatus status) {
        EmployeeComplaint c = require(id);
        if (!PLAIN.contains(status)) {
            throw ApiException.badRequest(EEmployeeError.EMP_096, c.getStatus().name(), status.name());
        }
        LifecycleSupport.assertMove(MOVES, c.getStatus(), status);
        c.setStatus(status);
        return detail(c);
    }

    /** Sets the handler and ASSIGNED (also a re-assignment from ASSIGNED / IN_PROGRESS). */
    @Transactional
    public ComplaintDto assign(Long id, AssignForm f) {
        EmployeeComplaint c = require(id);
        LifecycleSupport.assertMove(MOVES, c.getStatus(), EStatus.ASSIGNED);
        if (f.assignedToId().equals(c.getAgainstId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_069);
        }
        Employee handler = sameCompany(f.assignedToId(), c.getCompanyId());
        if (f.resolutionDeadline() != null && f.resolutionDeadline().isBefore(c.getComplaintDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "resolution deadline", "complaint date");
        }
        c.setAssignedToId(handler.getId());
        c.setResolutionDeadline(f.resolutionDeadline());
        c.setStatus(EStatus.ASSIGNED);
        return detail(c);
    }

    /** Records the resolution (full replacement) and sets RESOLVED. */
    @Transactional
    public ComplaintDto resolve(Long id, ResolveForm f) {
        EmployeeComplaint c = require(id);
        LifecycleSupport.assertMove(MOVES, c.getStatus(), EStatus.RESOLVED);
        if (f.resolutionDate().isBefore(c.getComplaintDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "resolution date", "complaint date");
        }
        checkFollowUp(f.followUpDate(), f.resolutionDate());
        c.setResolutionTypeId(catalogs.usable(ELifecycleCatalogKind.RESOLUTION_TYPE, f.resolutionTypeId(),
                c.getCompanyId(), c.getResolutionTypeId()).getId());
        c.setInvestigationNotes(AwardService.blankToNull(f.investigationNotes()));
        c.setResolutionAction(AwardService.blankToNull(f.resolutionAction()));
        c.setResolutionDate(f.resolutionDate());
        c.setFollowUpAction(AwardService.blankToNull(f.followUpAction()));
        c.setFollowUpDate(f.followUpDate());
        c.setStatus(EStatus.RESOLVED);
        return detail(c);
    }

    /** Follow-up modal (full replacement of its three fields); only once resolved. */
    @Transactional
    public ComplaintDto followUp(Long id, FollowUpForm f) {
        EmployeeComplaint c = require(id);
        if (c.getStatus() != EStatus.RESOLVED) {
            throw ApiException.badRequest(EEmployeeError.EMP_070);
        }
        checkFollowUp(f.followUpDate(), c.getResolutionDate());
        c.setFollowUpAction(AwardService.blankToNull(f.followUpAction()));
        c.setFollowUpDate(f.followUpDate());
        c.setFeedback(AwardService.blankToNull(f.feedback()));
        return detail(c);
    }

    /** Only while PENDING (manual §20). */
    @Transactional
    public void delete(Long id) {
        requirePending(id).setDeleted(true);
    }

    private EmployeeComplaint requirePending(Long id) {
        EmployeeComplaint c = require(id);
        if (c.getStatus() != EStatus.PENDING) {
            throw ApiException.badRequest(EEmployeeError.EMP_102, c.getStatus().name());
        }
        return c;
    }

    private static void checkFollowUp(LocalDate followUp, LocalDate resolution) {
        if (followUp != null && resolution != null && followUp.isBefore(resolution)) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "follow-up date", "resolution date");
        }
    }

    private void apply(EmployeeComplaint c, ComplaintForm f) {
        Employee complainant = support.employee(f.complainantId());
        Long companyId = complainant.getCompanyId();
        if (f.againstId() != null) {
            if (f.againstId().equals(complainant.getId())) {
                throw ApiException.badRequest(EEmployeeError.EMP_068);
            }
            sameCompany(f.againstId(), companyId);
        }
        Long typeId = catalogs.usable(ELifecycleCatalogKind.COMPLAINT_TYPE, f.complaintTypeId(), companyId,
                c.getComplaintTypeId()).getId();
        c.setCompanyId(companyId);
        c.setComplainantId(complainant.getId());
        c.setAgainstId(f.againstId());
        c.setComplaintTypeId(typeId);
        c.setSubject(f.subject().trim());
        c.setComplaintDate(f.complaintDate());
        c.setDescription(AwardService.blankToNull(f.description()));
        c.setDocumentMediaId(support.file(f.documentMediaId(), companyId));
        c.setAnonymous(f.anonymous());
    }

    /** Another employee of the complaint's company; any other id reads as not found. */
    private Employee sameCompany(Long employeeId, Long companyId) {
        Employee e = support.employee(employeeId);
        if (!Objects.equals(e.getCompanyId(), companyId)) {
            throw ApiException.notFound(EEmployeeError.EMP_001);
        }
        return e;
    }

    private static boolean canSeeAnonymous() {
        return CurrentUser.find().map(p -> p.has(MANAGE)).orElse(false);
    }

    private static void collect(EmployeeComplaint c, Set<Long> people, Set<Long> lists) {
        people.add(c.getComplainantId());
        if (c.getAgainstId() != null) {
            people.add(c.getAgainstId());
        }
        if (c.getAssignedToId() != null) {
            people.add(c.getAssignedToId());
        }
        lists.add(c.getComplaintTypeId());
        if (c.getResolutionTypeId() != null) {
            lists.add(c.getResolutionTypeId());
        }
    }

    private Map<Long, String> names(Set<Long> ids) {
        return catalogItems.findAllById(ids).stream()
                .collect(Collectors.toMap(LifecycleCatalog::getId, LifecycleCatalog::getName));
    }

    private ComplaintDto detail(EmployeeComplaint c) {
        Set<Long> people = new HashSet<>();
        Set<Long> lists = new HashSet<>();
        collect(c, people, lists);
        return dto(c, support.refs(people), names(lists), canSeeAnonymous(), true);
    }

    private ComplaintDto dto(EmployeeComplaint c, Map<Long, EmployeeRef> refs, Map<Long, String> names,
                             boolean manager, boolean withFile) {
        Long rt = c.getResolutionTypeId();
        return new ComplaintDto(c.getId().toString(),
                c.isAnonymous() && !manager ? null : refs.get(c.getComplainantId()), c.isAnonymous(),
                c.getAgainstId() == null ? null : refs.get(c.getAgainstId()),
                new Ref(c.getComplaintTypeId().toString(), names.get(c.getComplaintTypeId())), c.getSubject(),
                c.getComplaintDate(), c.getDescription(),
                c.getDocumentMediaId() == null ? null : c.getDocumentMediaId().toString(),
                withFile ? support.fileRef(c.getDocumentMediaId()) : null, c.getStatus(),
                new Assignment(c.getAssignedToId() == null ? null : refs.get(c.getAssignedToId()),
                        c.getResolutionDeadline()),
                new Resolution(rt == null ? null : new Ref(rt.toString(), names.get(rt)), c.getInvestigationNotes(),
                        c.getResolutionAction(), c.getResolutionDate()),
                new FollowUp(c.getFollowUpAction(), c.getFollowUpDate(), c.getFeedback()),
                c.getCreatedDate(), c.getUpdatedDate());
    }

    private EmployeeComplaint require(Long id) {
        EmployeeComplaint c = complaints.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_067));
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_067);
        return c;
    }
}
