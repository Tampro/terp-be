package com.terp.erp.recruitment.offer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Offers page and kanban (docs/analysis/06 §7.1.4), decided with the PO on 2026-11-20: the position is a job posting
 * (default the one the candidate applied for), the department an employee-service department (default the posting's),
 * Approved By an employee (name copied). Creating an offer moves a NEW / SCREENING / INTERVIEW candidate to OFFER and
 * refuses a HIRED / REJECTED one (REC_036); ACCEPTED makes the candidate HIRED; other statuses leave them. An offer past
 * its expiration date is flagged (expired) while still open, and a nightly job sets EXPIRED (PO 2026-11-21).
 * Status moves freely between DRAFT, SENT and NEGOTIATING; ACCEPTED, DECLINED and EXPIRED are final: no edit, delete,
 * status move or approval change any more (REC_067, manual §20), nor once the candidate was converted to an employee.
 * TODO(assumption): SENT and NEGOTIATING offers stay editable because negotiating changes the terms (the edit sends
 * the approval back to PENDING); an EXPIRED offer is not reopened — a new offer is made — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class OfferService {
    static final int KANBAN_CARDS = 50;
    /** TODO(assumption): "today" is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<Offer.EStatus> OPEN = EnumSet.of(Offer.EStatus.DRAFT, Offer.EStatus.SENT,
            Offer.EStatus.NEGOTIATING);

    private final OfferRepository offers;
    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final JobPostingService postingService;
    private final JobPostingRepository postings;
    private final CatalogItemRepository catalogItems;
    private final OrgClient org;
    private final EmployeeClient employees;
    private final Clock clock;

    public record Ref(String id, String name) {
    }

    public record JobRef(String id, String jobCode, String title) {
    }

    public record CandidateRef(String id, String fullName, String email) {
    }

    /** expired = past the expiration date while still Draft / Sent / Negotiating. */
    public record OfferDto(String id, CandidateRef candidate, JobRef job, Ref department, Ref jobType,
                           BigDecimal salary, BigDecimal bonus, String equity, LocalDate startDate, LocalDate expirationDate, boolean expired,
                           Ref approvedBy, String benefits, Offer.EStatus status,
                           Offer.EApprovalStatus approvalStatus, String approvalDecidedBy, Instant approvalDecidedAt,
                           LocalDate offerDate, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<Offer.EStatus, Long> byStatus) {
    }

    public record KanbanColumn(Offer.EStatus status, long count, List<OfferDto> cards) {
    }

    public record OfferForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long candidateId,
            @Schema(description = "Job posting offered; omitted = the one the candidate applied for", example = "1")
            Long jobPostingId,
            @Schema(description = "employee-service department; omitted = the posting's", example = "3")
            Long departmentId,
            @Schema(example = "25000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            @Digits(integer = 13, fraction = 2) BigDecimal salary,
            @Schema(description = "Bonus amount; omitted = none", example = "5000000") @DecimalMin("0")
            @Digits(integer = 15, fraction = 4) BigDecimal bonus,
            @Schema(description = "Equity offered, free text", example = "0.1% vesting 4y") @Size(max = 255)
            String equity,
            @Schema(example = "2027-01-04") LocalDate startDate,
            @Schema(example = "2026-12-15") LocalDate expirationDate,
            @Schema(description = "Employee id of the approver", example = "5") Long approvedByEmployeeId,
            @Schema(example = "Laptop, 13th month salary") @Size(max = 5000) String benefits) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Offer.EStatus status) {
    }

    public record ApprovalForm(
            @Schema(description = "APPROVED or REJECTED; PENDING withdraws the decision", example = "APPROVED",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Offer.EApprovalStatus approvalStatus) {
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<OfferDto> search(Long requestedCompanyId, String keyword, Long candidateId, Offer.EStatus status,
                                 Pageable pageable) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Page<Offer> page = offers.search(companyId, candidateId, status, Texts.kw(keyword), pageable);
        Lookups l = lookups(page.getContent());
        return page.map(o -> dto(o, l));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long candidateId) {
        Map<Offer.EStatus, Long> by = counts(CatalogService.company(requestedCompanyId), keyword, candidateId);
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public List<KanbanColumn> kanban(Long requestedCompanyId, String keyword, Long candidateId) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Map<Offer.EStatus, Long> by = counts(companyId, keyword, candidateId);
        List<KanbanColumn> out = new ArrayList<>();
        for (Offer.EStatus s : Offer.EStatus.values()) {
            List<Offer> cards = offers.search(companyId, candidateId, s, Texts.kw(keyword),
                    PageRequest.of(0, KANBAN_CARDS, Sort.by(Sort.Direction.DESC, "updatedDate"))).getContent();
            Lookups l = lookups(cards);
            out.add(new KanbanColumn(s, by.get(s), cards.stream().map(o -> dto(o, l)).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public OfferDto get(Long id) {
        Offer o = require(id);
        return dto(o, lookups(List.of(o)));
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public OfferDto create(OfferForm form) {
        Offer o = new Offer();
        o.setOfferDate(today());
        apply(o, form);
        offers.save(o);
        return dto(o, lookups(List.of(o)));
    }

    /**
     * Full replacement of the form fields; status and offer date are kept; the approval goes back to PENDING.
     * TODO(assumption): any edit needs a new approval (the approver approved other terms) — confirm with BA.
     */
    @Transactional
    public OfferDto update(Long id, OfferForm form) {
        Offer o = requireOpen(id);
        apply(o, form);
        decide(o, Offer.EApprovalStatus.PENDING);
        return dto(o, lookups(List.of(o)));
    }

    /** Update Status / kanban drag and drop; ACCEPTED makes the candidate HIRED. */
    @Transactional
    public OfferDto changeStatus(Long id, Offer.EStatus status) {
        Offer o = requireOpen(id);
        // TODO(assumption): an offer is approved before it is sent (REC_058); other moves are not gated — confirm with BA
        if (status == Offer.EStatus.SENT && o.getStatus() != Offer.EStatus.SENT
                && o.getApprovalStatus() != Offer.EApprovalStatus.APPROVED) {
            throw ApiException.badRequest(ERecruitmentError.REC_058);
        }
        o.setStatus(status);
        if (status == Offer.EStatus.ACCEPTED) {
            candidates.findById(o.getCandidateId()).ifPresent(c -> c.setStatus(Candidate.EStatus.HIRED));
        }
        return dto(o, lookups(List.of(o)));
    }

    /** Approve / reject (or back to PENDING); records who decided and when. */
    @Transactional
    public OfferDto approve(Long id, Offer.EApprovalStatus approval) {
        Offer o = requireOpen(id);
        decide(o, approval);
        return dto(o, lookups(List.of(o)));
    }

    private void decide(Offer o, Offer.EApprovalStatus approval) {
        if (approval == Offer.EApprovalStatus.PENDING) {
            o.setApprovalDecidedBy(null);
            o.setApprovalDecidedAt(null);
        } else {
            o.setApprovalDecidedBy(com.terp.erp.security.CurrentUser.find()
                    .map(com.terp.erp.security.ErpPrincipal::keycloakId).orElse(null));
            o.setApprovalDecidedAt(clock.instant());
        }
        o.setApprovalStatus(approval);
    }

    @Transactional
    public void delete(Long id) {
        requireOpen(id).setDeleted(true);
    }

    static final Set<Offer.EStatus> FINAL = EnumSet.of(Offer.EStatus.ACCEPTED, Offer.EStatus.DECLINED,
            Offer.EStatus.EXPIRED);

    /** Not final and its candidate not converted to an employee (REC_067). */
    private Offer requireOpen(Long id) {
        Offer o = require(id);
        boolean converted = candidates.findById(o.getCandidateId()).map(c -> c.getEmployeeId() != null)
                .orElse(false);
        if (FINAL.contains(o.getStatus()) || converted) {
            throw ApiException.badRequest(ERecruitmentError.REC_067, o.getStatus().name());
        }
        return o;
    }

    private void apply(Offer o, OfferForm f) {
        Candidate c = candidateService.require(f.candidateId());
        boolean newCandidate = !Objects.equals(c.getId(), o.getCandidateId());
        if (newCandidate && (c.getStatus() == Candidate.EStatus.HIRED
                || c.getStatus() == Candidate.EStatus.REJECTED)) {
            throw ApiException.badRequest(ERecruitmentError.REC_036);
        }
        JobPosting p = postingService.require(f.jobPostingId() != null ? f.jobPostingId() : c.getJobPostingId());
        if (f.startDate() != null && f.expirationDate() != null && f.expirationDate().isAfter(f.startDate())) {
            throw ApiException.badRequest(ERecruitmentError.REC_037);
        }
        Long departmentId = f.departmentId() != null ? f.departmentId() : p.getDepartmentId();
        if (departmentId != null && !departmentId.equals(o.getDepartmentId())) {
            org.department(departmentId);
        }
        if (f.approvedByEmployeeId() == null) {
            o.setApprovedByEmployeeId(null);
            o.setApprovedByName(null);
        } else if (!f.approvedByEmployeeId().equals(o.getApprovedByEmployeeId())) {
            o.setApprovedByName(employees.employee(f.approvedByEmployeeId()).fullName());
            o.setApprovedByEmployeeId(f.approvedByEmployeeId());
        }
        o.setCompanyId(c.getCompanyId());
        o.setCandidateId(c.getId());
        o.setJobPostingId(p.getId());
        o.setDepartmentId(departmentId);
        o.setSalary(f.salary());
        o.setBonus(f.bonus());
        o.setEquity(Texts.blankToNull(f.equity()));
        o.setStartDate(f.startDate());
        o.setExpirationDate(f.expirationDate());
        o.setBenefits(Texts.blankToNull(f.benefits()));
        if (newCandidate && (c.getStatus() == Candidate.EStatus.NEW || c.getStatus() == Candidate.EStatus.SCREENING
                || c.getStatus() == Candidate.EStatus.INTERVIEW)) {
            c.setStatus(Candidate.EStatus.OFFER);
        }
    }

    // ------------------------------------------------------------------ mapping

    private record Lookups(Map<Long, Candidate> candidates, Map<Long, JobPosting> postings, Map<Long, String> types,
                           Map<Long, String> departments, LocalDate today) {
    }

    private Lookups lookups(Collection<Offer> list) {
        Map<Long, JobPosting> ps = postings.findAllById(list.stream().map(Offer::getJobPostingId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        Map<Long, String> departments = new HashMap<>();
        list.stream().map(Offer::getDepartmentId).filter(Objects::nonNull).distinct()
                .forEach(id -> departments.put(id, org.departmentName(id)));
        return new Lookups(
                candidates.findAllById(list.stream().map(Offer::getCandidateId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Candidate::getId, Function.identity())),
                ps,
                catalogItems.findAllById(ps.values().stream().map(JobPosting::getJobTypeId)
                        .collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(CatalogItem::getId, CatalogItem::getName)),
                departments, today());
    }

    private static OfferDto dto(Offer o, Lookups l) {
        Candidate c = l.candidates().get(o.getCandidateId());
        JobPosting p = l.postings().get(o.getJobPostingId());
        return new OfferDto(o.getId().toString(),
                new CandidateRef(o.getCandidateId().toString(), c == null ? null : c.fullName(),
                        c == null ? null : c.getEmail()),
                p == null ? new JobRef(o.getJobPostingId().toString(), null, null)
                        : new JobRef(p.getId().toString(), p.getJobCode(), p.getTitle()),
                o.getDepartmentId() == null ? null
                        : new Ref(o.getDepartmentId().toString(), l.departments().get(o.getDepartmentId())),
                p == null ? null : new Ref(p.getJobTypeId().toString(), l.types().get(p.getJobTypeId())),
                o.getSalary(), o.getBonus(), o.getEquity(), o.getStartDate(), o.getExpirationDate(), expired(o, l.today()),
                o.getApprovedByEmployeeId() == null ? null
                        : new Ref(o.getApprovedByEmployeeId().toString(), o.getApprovedByName()),
                o.getBenefits(), o.getStatus(), o.getApprovalStatus(), o.getApprovalDecidedBy(),
                o.getApprovalDecidedAt(), o.getOfferDate(), o.getCreatedDate(), o.getUpdatedDate());
    }

    /** Nightly: open offers past their expiration date (Vietnam date) become EXPIRED. */
    @Transactional
    public int expireOverdue() {
        return offers.expireOverdue(LocalDate.now(clock.withZone(ZONE)), OPEN, clock.instant());
    }

    static boolean expired(Offer o, LocalDate today) {
        return o.getExpirationDate() != null && o.getExpirationDate().isBefore(today) && OPEN.contains(o.getStatus());
    }

    private Map<Offer.EStatus, Long> counts(Long companyId, String keyword, Long candidateId) {
        Map<Offer.EStatus, Long> by = new EnumMap<>(Offer.EStatus.class);
        for (Offer.EStatus s : Offer.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : offers.countByStatus(companyId, candidateId, Texts.kw(keyword))) {
            by.put((Offer.EStatus) r[0], (Long) r[1]);
        }
        return by;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    Offer require(Long id) {
        Offer o = offers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_035));
        com.terp.erp.security.TenantGuard.assertSameCompany(o.getCompanyId(), ERecruitmentError.REC_035);
        return o;
    }
}
