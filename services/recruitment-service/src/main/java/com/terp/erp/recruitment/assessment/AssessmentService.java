package com.terp.erp.recruitment.assessment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidate Assessments (docs/analysis/06 §7.1.6): score out of a max score (REC_046), outcome chosen by hand,
 * Conducted By an employee (name copied).
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {
    private final CandidateAssessmentRepository assessments;
    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final EmployeeClient employees;

    public record Ref(String id, String name) {
    }

    /** percentage = score / maxScore × 100, one decimal. */
    public record AssessmentDto(String id, Ref candidate, String name, BigDecimal score, BigDecimal maxScore,
                                BigDecimal percentage, CandidateAssessment.EStatus status, Ref conductedBy,
                                LocalDate assessmentDate, String comments, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<CandidateAssessment.EStatus, Long> byStatus) {
    }

    public record AssessmentForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long candidateId,
            @Schema(example = "Technical Skills Assessment", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String name,
            @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
            @Digits(integer = 7, fraction = 2) BigDecimal score,
            @Schema(example = "50", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 7, fraction = 2) BigDecimal maxScore,
            @Schema(description = "Omitted = PENDING on create, kept on edit") CandidateAssessment.EStatus status,
            @Schema(description = "Employee id", example = "5") Long conductedByEmployeeId,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate assessmentDate,
            @Size(max = 5000) String comments) {
    }

    @Transactional(readOnly = true)
    public Page<AssessmentDto> search(Long requestedCompanyId, String keyword, Long candidateId,
                                      CandidateAssessment.EStatus status, Pageable pageable) {
        Page<CandidateAssessment> page = assessments.search(CatalogService.company(requestedCompanyId), candidateId,
                status, Texts.kw(keyword), pageable);
        Map<Long, Candidate> cs = candidates.findAllById(page.map(CandidateAssessment::getCandidateId).toSet())
                .stream().collect(Collectors.toMap(Candidate::getId, Function.identity()));
        return page.map(a -> dto(a, cs.get(a.getCandidateId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long candidateId) {
        Map<CandidateAssessment.EStatus, Long> by = new EnumMap<>(CandidateAssessment.EStatus.class);
        for (CandidateAssessment.EStatus s : CandidateAssessment.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : assessments.countByStatus(CatalogService.company(requestedCompanyId), candidateId,
                Texts.kw(keyword))) {
            by.put((CandidateAssessment.EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public AssessmentDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public AssessmentDto create(AssessmentForm form) {
        CandidateAssessment a = new CandidateAssessment();
        apply(a, form);
        return dto(assessments.save(a));
    }

    /** Full replacement; status omitted = kept. */
    @Transactional
    public AssessmentDto update(Long id, AssessmentForm form) {
        CandidateAssessment a = require(id);
        apply(a, form);
        return dto(a);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(CandidateAssessment a, AssessmentForm f) {
        if (f.score().compareTo(f.maxScore()) > 0) {
            throw ApiException.badRequest(ERecruitmentError.REC_046);
        }
        Candidate c = candidateService.require(f.candidateId());
        if (f.conductedByEmployeeId() == null) {
            a.setConductedByEmployeeId(null);
            a.setConductedByName(null);
        } else if (!f.conductedByEmployeeId().equals(a.getConductedByEmployeeId())) {
            a.setConductedByName(employees.employee(f.conductedByEmployeeId()).fullName());
            a.setConductedByEmployeeId(f.conductedByEmployeeId());
        }
        a.setCompanyId(c.getCompanyId());
        a.setCandidateId(c.getId());
        a.setName(f.name().trim());
        a.setScore(f.score());
        a.setMaxScore(f.maxScore());
        if (f.status() != null) {
            a.setStatus(f.status());
        }
        a.setAssessmentDate(f.assessmentDate());
        a.setComments(Texts.blankToNull(f.comments()));
    }

    private AssessmentDto dto(CandidateAssessment a) {
        return dto(a, candidates.findById(a.getCandidateId()).orElse(null));
    }

    static BigDecimal percentage(BigDecimal score, BigDecimal max) {
        return score.multiply(BigDecimal.valueOf(100)).divide(max, 1, RoundingMode.HALF_UP);
    }

    private static AssessmentDto dto(CandidateAssessment a, Candidate c) {
        return new AssessmentDto(a.getId().toString(),
                new Ref(a.getCandidateId().toString(), c == null ? null : c.fullName()), a.getName(), a.getScore(),
                a.getMaxScore(), percentage(a.getScore(), a.getMaxScore()), a.getStatus(),
                a.getConductedByEmployeeId() == null ? null
                        : new Ref(a.getConductedByEmployeeId().toString(), a.getConductedByName()),
                a.getAssessmentDate(), a.getComments(), a.getCreatedDate(), a.getUpdatedDate());
    }

    private CandidateAssessment require(Long id) {
        CandidateAssessment a = assessments.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_041));
        TenantGuard.assertSameCompany(a.getCompanyId(), ERecruitmentError.REC_041);
        return a;
    }
}
