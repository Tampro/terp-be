package com.terp.erp.recruitment.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Candidate Onboarding (docs/analysis/06 §7.1.5), decided with the PO on 2026-11-20: the new hire is an existing
 * employee-service employee (name / email copied; one onboarding per employee, REC_043), optionally linked to the
 * HIRED candidate they came from, whose employeeId is then set (REC_044 / REC_045). The status is set by hand; the
 * details show the checklist's current active items with their due dates.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final CandidateOnboardingRepository onboardings;
    private final ChecklistService checklistService;
    private final OnboardingChecklistRepository checklists;
    private final ChecklistItemRepository items;
    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final EmployeeClient employees;

    public record Ref(String id, String name) {
    }

    public record EmployeeRef(String id, String name, String email) {
    }

    public record OnboardingRow(String id, EmployeeRef employee, Ref checklist, LocalDate startDate, Ref buddy,
                                String candidateId, CandidateOnboarding.EStatus status, Instant createdDate) {
    }

    /** dueDate = start date + due day. */
    public record TaskDto(String id, String taskName, String description, ChecklistItem.ECategory category,
                          boolean required, Integer dueDay, LocalDate dueDate) {
    }

    public record ChecklistView(String id, String name, String description, List<TaskDto> items) {
    }

    public record OnboardingDetail(String id, EmployeeRef employee, Ref buddy, String candidateId, LocalDate startDate,
                                   CandidateOnboarding.EStatus status, ChecklistView checklist, Instant createdDate,
                                   Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<CandidateOnboarding.EStatus, Long> byStatus) {
    }

    public record OnboardingForm(
            @Schema(description = "Employee id (employee-service)", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long checklistId,
            @Schema(example = "2027-01-04", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(description = "Buddy employee id", example = "2") Long buddyEmployeeId,
            @Schema(description = "The hired candidate this employee came from", example = "3") Long candidateId) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                             CandidateOnboarding.EStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<OnboardingRow> search(Long requestedCompanyId, String keyword, Long employeeId,
                                      CandidateOnboarding.EStatus status, Pageable pageable) {
        Page<CandidateOnboarding> page = onboardings.search(CatalogService.company(requestedCompanyId), employeeId,
                status, Texts.kw(keyword), pageable);
        Map<Long, String> names = checklists.findAllById(page.map(CandidateOnboarding::getChecklistId).toSet())
                .stream().collect(Collectors.toMap(OnboardingChecklist::getId, OnboardingChecklist::getName));
        return page.map(o -> new OnboardingRow(o.getId().toString(), employee(o),
                new Ref(o.getChecklistId().toString(), names.get(o.getChecklistId())), o.getStartDate(), buddy(o),
                o.getCandidateId() == null ? null : o.getCandidateId().toString(), o.getStatus(), o.getCreatedDate()));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId) {
        Map<CandidateOnboarding.EStatus, Long> by = new EnumMap<>(CandidateOnboarding.EStatus.class);
        for (CandidateOnboarding.EStatus s : CandidateOnboarding.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : onboardings.countByStatus(CatalogService.company(requestedCompanyId), employeeId,
                Texts.kw(keyword))) {
            by.put((CandidateOnboarding.EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public OnboardingDetail get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public OnboardingDetail create(OnboardingForm form) {
        CandidateOnboarding o = new CandidateOnboarding();
        apply(o, form);
        onboardings.save(o);
        return detail(o);
    }

    /** Full replacement; the status is kept. */
    @Transactional
    public OnboardingDetail update(Long id, OnboardingForm form) {
        CandidateOnboarding o = require(id);
        apply(o, form);
        return detail(o);
    }

    @Transactional
    public OnboardingDetail changeStatus(Long id, CandidateOnboarding.EStatus status) {
        CandidateOnboarding o = require(id);
        o.setStatus(status);
        return detail(o);
    }

    /** The linked candidate is released (no longer shown as converted). */
    @Transactional
    public void delete(Long id) {
        CandidateOnboarding o = require(id);
        unlink(o);
        o.setDeleted(true);
    }

    private void apply(CandidateOnboarding o, OnboardingForm f) {
        if (Objects.equals(f.employeeId(), f.buddyEmployeeId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_042);
        }
        OnboardingChecklist c = checklistService.requireChecklist(f.checklistId());
        if (c.getStatus() != EStatus.ACTIVE && !Objects.equals(c.getId(), o.getChecklistId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_014, "checklist");
        }
        // the checklist's company: a company user's own, or the one a super admin picked it from; fixed afterwards
        if (o.getCompanyId() != null && !o.getCompanyId().equals(c.getCompanyId())) {
            throw ApiException.notFound(ERecruitmentError.REC_038);
        }
        o.setCompanyId(c.getCompanyId());
        if (onboardings.employeeTaken(c.getCompanyId(), f.employeeId(), o.getId() == null ? 0L : o.getId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_043);
        }
        if (!f.employeeId().equals(o.getEmployeeId())) {
            EmployeeClient.Employee e = employees.employee(f.employeeId());
            o.setEmployeeId(f.employeeId());
            o.setEmployeeName(e.fullName());
            o.setEmployeeEmail(e.email());
        }
        if (f.buddyEmployeeId() == null) {
            o.setBuddyEmployeeId(null);
            o.setBuddyName(null);
        } else if (!f.buddyEmployeeId().equals(o.getBuddyEmployeeId())) {
            o.setBuddyName(employees.employee(f.buddyEmployeeId()).fullName());
            o.setBuddyEmployeeId(f.buddyEmployeeId());
        }
        if (!Objects.equals(f.candidateId(), o.getCandidateId())) {
            unlink(o);
            o.setCandidateId(null);
        }
        if (f.candidateId() != null) {
            Candidate cand = candidateService.require(f.candidateId());
            if (!cand.getCompanyId().equals(o.getCompanyId())) {
                throw ApiException.notFound(ERecruitmentError.REC_015);
            }
            if (cand.getStatus() != Candidate.EStatus.HIRED) {
                throw ApiException.badRequest(ERecruitmentError.REC_044);
            }
            if (cand.getEmployeeId() != null && !cand.getEmployeeId().equals(o.getEmployeeId())) {
                throw ApiException.badRequest(ERecruitmentError.REC_045);
            }
            cand.setEmployeeId(o.getEmployeeId());
            o.setCandidateId(cand.getId());
        }
        o.setChecklistId(c.getId());
        o.setStartDate(f.startDate());
    }

    private void unlink(CandidateOnboarding o) {
        if (o.getCandidateId() != null) {
            candidates.findById(o.getCandidateId())
                    .filter(c -> Objects.equals(c.getEmployeeId(), o.getEmployeeId()))
                    .ifPresent(c -> c.setEmployeeId(null));
        }
    }

    private OnboardingDetail detail(CandidateOnboarding o) {
        OnboardingChecklist c = checklists.findById(o.getChecklistId()).orElse(null);
        List<TaskDto> tasks = items.activeOf(o.getChecklistId()).stream()
                .map(i -> new TaskDto(i.getId().toString(), i.getTaskName(), i.getDescription(), i.getCategory(),
                        i.isRequired(), i.getDueDay(),
                        i.getDueDay() == null ? null : o.getStartDate().plusDays(i.getDueDay())))
                .toList();
        ChecklistView view = new ChecklistView(o.getChecklistId().toString(), c == null ? null : c.getName(),
                c == null ? null : c.getDescription(), tasks);
        return new OnboardingDetail(o.getId().toString(), employee(o), buddy(o),
                o.getCandidateId() == null ? null : o.getCandidateId().toString(), o.getStartDate(), o.getStatus(),
                view, o.getCreatedDate(), o.getUpdatedDate());
    }

    private static EmployeeRef employee(CandidateOnboarding o) {
        return new EmployeeRef(o.getEmployeeId().toString(), o.getEmployeeName(), o.getEmployeeEmail());
    }

    private static Ref buddy(CandidateOnboarding o) {
        return o.getBuddyEmployeeId() == null ? null : new Ref(o.getBuddyEmployeeId().toString(), o.getBuddyName());
    }

    private CandidateOnboarding require(Long id) {
        CandidateOnboarding o = onboardings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_040));
        TenantGuard.assertSameCompany(o.getCompanyId(), ERecruitmentError.REC_040);
        return o;
    }
}
