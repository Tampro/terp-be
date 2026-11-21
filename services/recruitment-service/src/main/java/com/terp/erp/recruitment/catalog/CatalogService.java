package com.terp.erp.recruitment.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogDtos.CatalogDto;
import com.terp.erp.recruitment.catalog.CatalogDtos.CatalogForm;
import com.terp.erp.recruitment.catalog.CatalogDtos.Option;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.util.List;

/**
 * Job Categories, Job Types, Candidate Sources and Interview Types pages (docs/analysis/06 §7.1.10, 7.1.11, 7.1.13,
 * 7.1.14). Names are unique per company and kind.
 * A job type used by a job posting, a source used by a candidate, or an interview type used by an
 * interview cannot be deleted (REC_013).
 * TODO(assumption): job categories, candidate sources and interview types get the same check when candidates and
 * interviews use them (next PRs).
 */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CatalogItemRepository items;
    private final com.terp.erp.recruitment.posting.JobPostingRepository postings;
    private final com.terp.erp.recruitment.candidate.CandidateRepository candidates;
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews;

    @Transactional(readOnly = true)
    public Page<CatalogDto> search(ECatalogKind kind, Long requestedCompanyId, String keyword, EStatus status,
                                   Pageable pageable) {
        return items.search(company(requestedCompanyId), kind, status, Texts.kw(keyword), pageable).map(CatalogDto::of);
    }

    @Transactional(readOnly = true)
    public List<Option> options(ECatalogKind kind, Long requestedCompanyId) {
        return items.findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId), kind,
                EStatus.ACTIVE).stream().map(c -> new Option(c.getId().toString(), c.getName())).toList();
    }

    @Transactional(readOnly = true)
    public CatalogDto get(ECatalogKind kind, Long id) {
        return CatalogDto.of(require(kind, id));
    }

    @Transactional
    public CatalogDto create(ECatalogKind kind, Long requestedCompanyId, CatalogForm form) {
        CatalogItem c = new CatalogItem();
        c.setCompanyId(company(requestedCompanyId));
        c.setKind(kind);
        apply(c, form, -1L);
        return CatalogDto.of(items.save(c));
    }

    /** Full replacement: omitted description is cleared, omitted status is kept. */
    @Transactional
    public CatalogDto update(ECatalogKind kind, Long id, CatalogForm form) {
        CatalogItem c = require(kind, id);
        apply(c, form, id);
        return CatalogDto.of(c);
    }

    @Transactional
    public CatalogDto changeStatus(ECatalogKind kind, Long id, EStatus status) {
        CatalogItem c = require(kind, id);
        c.setStatus(status);
        return CatalogDto.of(c);
    }

    @Transactional
    public void delete(ECatalogKind kind, Long id) {
        CatalogItem c = require(kind, id);
        if (kind == ECatalogKind.JOB_TYPE && postings.existsByJobTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        if (kind == ECatalogKind.CANDIDATE_SOURCE && candidates.existsBySourceIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        if (kind == ECatalogKind.INTERVIEW_TYPE && interviews.existsByInterviewTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        c.setDeleted(true);
    }

    private void apply(CatalogItem c, CatalogForm f, Long selfId) {
        String name = f.name().trim();
        if (items.nameTaken(c.getCompanyId(), c.getKind(), name, selfId)) {
            throw ApiException.badRequest(ERecruitmentError.REC_004);
        }
        c.setName(name);
        c.setDescription(Texts.blankToNull(f.description()));
        if (f.status() != null) {
            c.setStatus(f.status());
        }
    }

    /** An item of that kind and of the caller's company; the same id under another kind is not found. */
    public CatalogItem require(ECatalogKind kind, Long id) {
        CatalogItem c = items.findByIdAndKindAndDeletedFalse(id, kind)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_001));
        TenantGuard.assertSameCompany(c.getCompanyId(), ERecruitmentError.REC_001);
        return c;
    }

    public static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, ERecruitmentError.REC_005);
    }
}
