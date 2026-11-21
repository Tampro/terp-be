package com.terp.erp.recruitment.posting;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.HtmlSanitizer;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.location.JobLocation;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.location.JobLocationService;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingDto;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingForm;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingOption;
import com.terp.erp.recruitment.posting.JobPostingDtos.QuestionRef;
import com.terp.erp.recruitment.posting.JobPostingDtos.Ref;
import com.terp.erp.recruitment.question.CustomQuestion;
import com.terp.erp.recruitment.question.CustomQuestionRepository;
import com.terp.erp.recruitment.question.CustomQuestionService;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Job Postings page (docs/analysis/06 §7.1.1). The job code (JOB-0001...) is generated per company; branch and
 * department are checked in employee-service as the caller, the department must belong to the branch (REC_010); the job
 * type and location must be active items of the company (REC_014); rich text is sanitized.
 */
@Service
@RequiredArgsConstructor
public class JobPostingService {
    static final String SEQUENCE = "JOB";

    private final JobPostingRepository postings;
    private final CatalogService catalog;
    private final CatalogItemRepository catalogItems;
    private final JobLocationService locationService;
    private final JobLocationRepository locations;
    private final CustomQuestionService questionService;
    private final CustomQuestionRepository questions;
    private final OrgClient org;
    private final Clock clock;
    private final com.terp.erp.recruitment.candidate.CandidateRepository candidates;

    @Transactional(readOnly = true)
    public Page<PostingDto> search(Long requestedCompanyId, String keyword, JobPosting.EStatus status, Boolean published,
                                   Pageable pageable) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Page<JobPosting> page = postings.search(companyId, status, published, Texts.kw(keyword), pageable);
        Map<Long, String> typeNames = catalogItems.findAllById(page.getContent().stream().map(JobPosting::getJobTypeId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(CatalogItem::getId, CatalogItem::getName));
        Map<Long, String> locationNames = locations.findAllById(page.getContent().stream()
                        .map(JobPosting::getLocationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(JobLocation::getId, JobLocation::getName));
        Map<Long, Long> applications = new java.util.HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : candidates.countByPosting(page.getContent().stream().map(JobPosting::getId).toList())) {
                applications.put((Long) r[0], (Long) r[1]);
            }
        }
        return page.map(p -> dto(p, typeNames.get(p.getJobTypeId()), locationNames.get(p.getLocationId()), null, null,
                false, applications.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<PostingOption> options(Long requestedCompanyId) {
        return postings.findByCompanyIdAndDeletedFalseOrderByTitleAsc(CatalogService.company(requestedCompanyId))
                .stream().map(p -> new PostingOption(p.getId().toString(), p.getJobCode(), p.getTitle())).toList();
    }

    @Transactional(readOnly = true)
    public PostingDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public PostingDto create(Long requestedCompanyId, PostingForm form) {
        JobPosting p = new JobPosting();
        p.setCompanyId(CatalogService.company(requestedCompanyId));
        apply(p, form);
        p.setJobCode(nextCode(p.getCompanyId()));
        postings.save(p);
        return detail(p);
    }

    /** Full replacement of the form fields; code, status and published state are kept. */
    @Transactional
    public PostingDto update(Long id, PostingForm form) {
        JobPosting p = require(id);
        apply(p, form);
        return detail(p);
    }

    /** Publish = status PUBLISHED, shown on the career page; unpublish = back to DRAFT. */
    @Transactional
    public PostingDto setPublished(Long id, boolean published) {
        JobPosting p = require(id);
        p.setPublished(published);
        p.setStatus(published ? JobPosting.EStatus.PUBLISHED : JobPosting.EStatus.DRAFT);
        if (published) {
            p.setPublishDate(clock.instant());
            // refresh the copies shown on the career page; a renamed branch shows its new name from here
            String branch = org.branchName(p.getBranchId());
            if (branch != null) {
                p.setBranchName(branch);
            }
            String department = org.departmentName(p.getDepartmentId());
            if (department != null) {
                p.setDepartmentName(department);
            }
        }
        return detail(p);
    }

    @Transactional
    public void delete(Long id) {
        JobPosting p = require(id);
        if (candidates.existsByJobPostingIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ERecruitmentError.REC_013);
        }
        p.setDeleted(true);
    }

    private void apply(JobPosting p, PostingForm f) {
        CatalogItem type = catalog.require(ECatalogKind.JOB_TYPE, f.jobTypeId());
        if (type.getStatus() != EStatus.ACTIVE && !Objects.equals(type.getId(), p.getJobTypeId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_014, "job type");
        }
        JobLocation loc = locationService.require(f.locationId());
        if (loc.getStatus() != EStatus.ACTIVE && !Objects.equals(loc.getId(), p.getLocationId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_014, "job location");
        }
        assertRange(f.minExperience(), f.maxExperience(), "experience");
        assertRange(f.minSalary(), f.maxSalary(), "salary");
        JobPosting.EApplicationType appType = f.applicationType() == null ? JobPosting.EApplicationType.EXISTING
                : f.applicationType();
        if (appType == JobPosting.EApplicationType.CUSTOM && Texts.blankToNull(f.applicationUrl()) == null) {
            throw ApiException.badRequest(ERecruitmentError.REC_009);
        }
        String branchName = null;
        String departmentName = null;
        if (f.departmentId() != null) {
            OrgClient.Department d = org.department(f.departmentId());
            if (f.branchId() == null || d.branch() == null || !String.valueOf(f.branchId()).equals(d.branch().id())) {
                throw ApiException.badRequest(ERecruitmentError.REC_010);
            }
            branchName = d.branch().name();
            departmentName = d.name();
        } else if (f.branchId() != null) {
            branchName = org.branch(f.branchId()).name();
        }
        List<Long> questionIds = f.customQuestionIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(f.customQuestionIds()));
        questionIds.forEach(questionService::require);
        p.setTitle(f.title().trim());
        p.setJobTypeId(type.getId());
        p.setLocationId(loc.getId());
        p.setBranchId(f.branchId());
        p.setDepartmentId(f.departmentId());
        p.setBranchName(branchName);
        p.setDepartmentName(departmentName);
        p.setPriority(f.priority() == null ? JobPosting.EPriority.MEDIUM : f.priority());
        p.setSkills(new ArrayList<>(f.skills() == null ? List.of()
                : f.skills().stream().map(String::trim).distinct().toList()));
        p.setStartDate(f.startDate());
        p.setApplicationDeadline(f.applicationDeadline());
        p.setApplicationType(appType);
        p.setApplicationUrl(appType == JobPosting.EApplicationType.CUSTOM ? f.applicationUrl().trim() : null);
        p.setPositions(f.positions() == null ? 1 : f.positions());
        p.setFeatured(f.featured());
        p.setMinExperience(f.minExperience());
        p.setMaxExperience(f.maxExperience());
        p.setMinSalary(f.minSalary());
        p.setMaxSalary(f.maxSalary());
        p.setDescription(rich(f.description()));
        p.setRequirements(rich(f.requirements()));
        p.setBenefits(rich(f.benefits()));
        p.getCustomQuestionIds().clear();
        p.getCustomQuestionIds().addAll(questionIds);
        p.setAskGender(f.askGender());
        p.setAskDateOfBirth(f.askDateOfBirth());
        p.setShowCoverLetter(f.showCoverLetter());
        p.setShowTerms(f.showTerms());
    }

    private static void assertRange(Integer min, Integer max, String what) {
        if (min != null && max != null && min > max) {
            throw ApiException.badRequest(ERecruitmentError.REC_008, what);
        }
    }

    private static void assertRange(BigDecimal min, BigDecimal max, String what) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ApiException.badRequest(ERecruitmentError.REC_008, what);
        }
    }

    private static String rich(String html) {
        String clean = html == null ? "" : HtmlSanitizer.clean(html);
        return clean.isEmpty() ? null : clean;
    }

    private String nextCode(Long companyId) {
        postings.ensureSequence(companyId, SEQUENCE);
        long next = postings.lockSequence(companyId, SEQUENCE);
        postings.bumpSequence(companyId, SEQUENCE);
        return "JOB-%04d".formatted(next);
    }

    private PostingDto detail(JobPosting p) {
        String type = catalogItems.findById(p.getJobTypeId()).map(CatalogItem::getName).orElse(null);
        String location = locations.findById(p.getLocationId()).map(JobLocation::getName).orElse(null);
        return dto(p, type, location, org.branchName(p.getBranchId()), org.departmentName(p.getDepartmentId()), true,
                candidates.countByJobPostingIdAndDeletedFalse(p.getId()));
    }

    private PostingDto dto(JobPosting p, String type, String location, String branch, String department,
                           boolean detail, long applications) {
        List<QuestionRef> qs = !detail ? null : questions.findAllById(p.getCustomQuestionIds()).stream()
                .filter(q -> !q.isDeleted()).sorted(Comparator.comparing(CustomQuestion::getId))
                .map(q -> new QuestionRef(q.getId().toString(), q.getQuestion(), q.isRequired())).toList();
        return new PostingDto(p.getId().toString(), p.getJobCode(), p.getTitle(),
                new Ref(p.getJobTypeId().toString(), type), new Ref(p.getLocationId().toString(), location),
                p.getBranchId() == null ? null : new Ref(p.getBranchId().toString(), branch),
                p.getDepartmentId() == null ? null : new Ref(p.getDepartmentId().toString(), department),
                p.getPriority(), List.copyOf(p.getSkills()), p.getStartDate(), p.getApplicationDeadline(),
                p.getApplicationType(), p.getApplicationUrl(), p.getPositions(), p.isFeatured(), p.getMinExperience(),
                p.getMaxExperience(), p.getMinSalary(), p.getMaxSalary(), detail ? p.getDescription() : null,
                detail ? p.getRequirements() : null, detail ? p.getBenefits() : null, qs, p.isAskGender(),
                p.isAskDateOfBirth(), p.isShowCoverLetter(), p.isShowTerms(), p.getStatus(), p.isPublished(),
                p.getPublishDate(), applications, p.getCreatedDate(), p.getUpdatedDate());
    }

    public JobPosting require(Long id) {
        JobPosting p = postings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_006));
        TenantGuard.assertSameCompany(p.getCompanyId(), ERecruitmentError.REC_006);
        return p;
    }
}
