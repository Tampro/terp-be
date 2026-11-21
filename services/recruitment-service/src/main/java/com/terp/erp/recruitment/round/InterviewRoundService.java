package com.terp.erp.recruitment.round;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogDtos.StatusCounts;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Interview Rounds page (docs/analysis/06 §7.1.15). A round belongs to a posting of the same company. */
@Service
@RequiredArgsConstructor
public class InterviewRoundService {
    private final InterviewRoundRepository rounds;
    private final JobPostingRepository postings;
    private final JobPostingService postingService;
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews;

    public record JobRef(String id, String jobCode, String title) {
    }

    public record RoundDto(String id, JobRef job, int sequenceNumber, String name, String description, EStatus status,
                           Instant createdDate, Instant updatedDate) {
    }

    public record RoundForm(
            @Schema(description = "Job posting id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long jobPostingId,
            @Schema(example = "Technical interview", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) @Max(100) int sequenceNumber,
            @Schema(example = "Live coding with the team") @Size(max = 1000) String description,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    /** Sorted by job, then sequence, by default. */
    @Transactional(readOnly = true)
    public Page<RoundDto> search(Long requestedCompanyId, Long jobPostingId, String keyword, EStatus status,
                                 Pageable pageable) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Page<InterviewRound> page = rounds.search(companyId, jobPostingId, status, Texts.kw(keyword), pageable);
        Map<Long, JobPosting> jobs = postings.findAllById(page.getContent().stream().map(InterviewRound::getJobPostingId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        return page.map(r -> dto(r, jobs.get(r.getJobPostingId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Long jobPostingId, String keyword) {
        long active = 0;
        long inactive = 0;
        for (Object[] row : rounds.countByStatus(CatalogService.company(requestedCompanyId), jobPostingId,
                Texts.kw(keyword))) {
            if (row[0] == EStatus.ACTIVE) {
                active = (Long) row[1];
            } else {
                inactive = (Long) row[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    /** Active rounds of a posting in order (scheduling interviews). */
    @Transactional(readOnly = true)
    public List<RoundDto> ofPosting(Long jobPostingId) {
        JobPosting p = postingService.require(jobPostingId);
        return rounds.findByJobPostingIdAndStatusAndDeletedFalseOrderBySequenceNumberAsc(p.getId(), EStatus.ACTIVE)
                .stream().map(r -> dto(r, p)).toList();
    }

    @Transactional(readOnly = true)
    public RoundDto get(Long id) {
        InterviewRound r = require(id);
        return dto(r, postings.findById(r.getJobPostingId()).orElse(null));
    }

    @Transactional
    public RoundDto create(RoundForm form) {
        JobPosting p = postingService.require(form.jobPostingId());
        InterviewRound r = new InterviewRound();
        r.setCompanyId(p.getCompanyId());
        apply(r, p, form, -1L);
        return dto(rounds.save(r), p);
    }

    /** Full replacement: omitted description is cleared, omitted status is kept. */
    @Transactional
    public RoundDto update(Long id, RoundForm form) {
        InterviewRound r = require(id);
        JobPosting p = postingService.require(form.jobPostingId());
        apply(r, p, form, id);
        return dto(r, p);
    }

    @Transactional
    public RoundDto changeStatus(Long id, EStatus status) {
        InterviewRound r = require(id);
        r.setStatus(status);
        return dto(r, postings.findById(r.getJobPostingId()).orElse(null));
    }

    @Transactional
    public void delete(Long id) {
        InterviewRound r = require(id);
        if (interviews.existsByRoundIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        r.setDeleted(true);
    }

    private void apply(InterviewRound r, JobPosting p, RoundForm f, Long selfId) {
        if (rounds.existsByJobPostingIdAndSequenceNumberAndDeletedFalseAndIdNot(p.getId(), f.sequenceNumber(), selfId)) {
            throw ApiException.badRequest(ERecruitmentError.REC_011);
        }
        r.setJobPostingId(p.getId());
        r.setName(f.name().trim());
        r.setSequenceNumber(f.sequenceNumber());
        r.setDescription(Texts.blankToNull(f.description()));
        if (f.status() != null) {
            r.setStatus(f.status());
        }
    }

    private static RoundDto dto(InterviewRound r, JobPosting p) {
        JobRef job = p == null ? new JobRef(r.getJobPostingId().toString(), null, null)
                : new JobRef(p.getId().toString(), p.getJobCode(), p.getTitle());
        return new RoundDto(r.getId().toString(), job, r.getSequenceNumber(), r.getName(), r.getDescription(),
                r.getStatus(), r.getCreatedDate(), r.getUpdatedDate());
    }

    private InterviewRound require(Long id) {
        InterviewRound r = rounds.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_007));
        TenantGuard.assertSameCompany(r.getCompanyId(), ERecruitmentError.REC_007);
        return r;
    }
}
