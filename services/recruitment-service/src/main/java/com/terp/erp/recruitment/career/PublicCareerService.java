package com.terp.erp.recruitment.career;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateDtos.AnswerForm;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateForm;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.location.JobLocation;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.question.CustomQuestion;
import com.terp.erp.recruitment.question.CustomQuestionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The public career page (docs/analysis/06 §7.1.9), no token: the company comes from the slug of an enabled
 * {@link CareerSettings} only, never from the request. Only published postings within their deadline are shown or
 * take applications; nothing internal (ids of other companies, audit fields, counts of candidates) is returned.
 * Applications become NEW candidates through {@link CandidateService#register}.
 */
@Service
@RequiredArgsConstructor
public class PublicCareerService {
    static final int SIMILAR = 4;
    /** TODO(assumption): "today" (deadline check) is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CareerSettingsRepository settings;
    private final JobPostingRepository postings;
    private final JobLocationRepository locations;
    private final CatalogItemRepository catalogItems;
    private final CustomQuestionRepository questions;
    private final CandidateService candidates;
    private final Clock clock;

    public record Ref(String id, String name) {
    }

    public record LocationRef(String id, String name, String city, boolean remote) {
    }

    public record Site(String slug, String displayName, String headline, String tagline, long openJobs) {
    }

    public record Filters(List<LocationRef> locations, List<Ref> jobTypes, List<Ref> sources) {
    }

    public record JobCard(String id, String title, List<String> skills, String branch, LocationRef location,
                          Ref jobType, int positions, boolean featured, JobPosting.EPriority priority,
                          BigDecimal minSalary, BigDecimal maxSalary, Instant publishDate,
                          LocalDate applicationDeadline) {
    }

    public record QuestionRef(String id, String question, boolean required) {
    }

    /** applicationUrl is set when the job takes applications elsewhere (CUSTOM). */
    public record JobDetail(JobCard job, String department, String description, String requirements,
                            String benefits, int minExperience, Integer maxExperience,
                            JobPosting.EApplicationType applicationType, String applicationUrl, boolean askGender,
                            boolean askDateOfBirth, boolean showCoverLetter, boolean showTerms,
                            List<QuestionRef> questions, List<JobCard> similarJobs) {
    }

    public record Applied(String jobTitle, LocalDate applicationDate) {
    }

    /** Gender / date of birth / cover letter message are kept only when the posting asks for them. */
    public record ApplicationForm(
            @Schema(example = "Test", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String firstName,
            @Schema(example = "Applicant", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String lastName,
            @Schema(example = "applicant@example.invalid", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            Candidate.EGender gender,
            @Past LocalDate dateOfBirth,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String zipCode,
            @Size(max = 100) String country,
            @Size(max = 150) String currentPosition,
            @Size(max = 150) String currentCompany,
            @Min(0) @Max(60) int experienceYears,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal currentSalary,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal expectedSalary,
            @Size(max = 5000) String coverLetterMessage,
            @Schema(description = "How did you hear about this position? (a source from /filters)") Long sourceId,
            boolean termsAccepted,
            @Size(max = 50) List<@Valid AnswerForm> answers) {
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Site site(String slug) {
        CareerSettings s = require(slug);
        return new Site(s.getSlug(), s.getDisplayName(), s.getHeadline(), s.getTagline(),
                postings.countOpen(s.getCompanyId(), today()));
    }

    @Transactional(readOnly = true)
    public Filters filters(String slug) {
        Long companyId = require(slug).getCompanyId();
        return new Filters(
                locations.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(companyId, EStatus.ACTIVE).stream()
                        .map(PublicCareerService::location).toList(),
                options(companyId, ECatalogKind.JOB_TYPE), options(companyId, ECatalogKind.CANDIDATE_SOURCE));
    }

    @Transactional(readOnly = true)
    public Page<JobCard> jobs(String slug, String keyword, Long locationId, Collection<Long> jobTypeIds,
                              BigDecimal minSalary, BigDecimal maxSalary, Integer minPositions, Integer maxPositions,
                              Pageable pageable) {
        Long companyId = require(slug).getCompanyId();
        boolean anyType = jobTypeIds == null || jobTypeIds.isEmpty();
        Page<JobPosting> page = postings.openJobs(companyId, today(), Texts.kw(keyword), locationId, anyType,
                anyType ? List.of(-1L) : jobTypeIds, minSalary, maxSalary, minPositions, maxPositions, pageable);
        Lookups l = lookups(page.getContent());
        return page.map(p -> card(p, l));
    }

    @Transactional(readOnly = true)
    public JobDetail job(String slug, Long id) {
        CareerSettings s = require(slug);
        JobPosting p = open(s, id);
        List<JobPosting> similar = postings.similarOpen(s.getCompanyId(), today(), p.getId(), p.getJobTypeId(),
                p.getDepartmentId(), PageRequest.of(0, SIMILAR));
        Lookups l = lookups(java.util.stream.Stream.concat(java.util.stream.Stream.of(p), similar.stream()).toList());
        List<QuestionRef> qs = questions.findAllById(p.getCustomQuestionIds()).stream().filter(q -> !q.isDeleted())
                .sorted(Comparator.comparing(CustomQuestion::getId))
                .map(q -> new QuestionRef(q.getId().toString(), q.getQuestion(), q.isRequired())).toList();
        boolean custom = p.getApplicationType() == JobPosting.EApplicationType.CUSTOM;
        return new JobDetail(card(p, l), p.getDepartmentName(), p.getDescription(), p.getRequirements(),
                p.getBenefits(), p.getMinExperience(), p.getMaxExperience(), p.getApplicationType(),
                custom ? p.getApplicationUrl() : null, p.isAskGender(), p.isAskDateOfBirth(), p.isShowCoverLetter(),
                p.isShowTerms(), qs, similar.stream().map(x -> card(x, l)).toList());
    }

    // ------------------------------------------------------------------ apply

    /** The candidate starts as NEW; the resume is required, the cover letter file optional. */
    @Transactional
    public Applied apply(String slug, Long id, ApplicationForm f, MultipartFile resume, MultipartFile coverLetter) {
        CareerSettings s = require(slug);
        JobPosting p = open(s, id);
        if (p.getApplicationType() == JobPosting.EApplicationType.CUSTOM) {
            throw ApiException.badRequest(ERecruitmentError.REC_052, p.getApplicationUrl());
        }
        if (p.isShowTerms() && !f.termsAccepted()) {
            throw ApiException.badRequest(ERecruitmentError.REC_049);
        }
        if (resume == null || resume.isEmpty()) {
            throw ApiException.badRequest(ERecruitmentError.REC_050);
        }
        if (f.sourceId() != null) {
            catalogItems.findByIdAndKindAndDeletedFalse(f.sourceId(), ECatalogKind.CANDIDATE_SOURCE)
                    .filter(c -> c.getCompanyId().equals(s.getCompanyId()) && c.getStatus() == EStatus.ACTIVE)
                    .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_001));
        }
        CandidateForm form = new CandidateForm(p.getId(), f.sourceId(), f.firstName(), f.lastName(), f.email(),
                f.phone(), f.currentCompany(), f.currentPosition(), f.experienceYears(), f.currentSalary(),
                f.expectedSalary(), null, p.isAskGender() ? f.gender() : null,
                p.isAskDateOfBirth() ? f.dateOfBirth() : null, f.address(), f.city(), f.state(), f.zipCode(),
                f.country(), p.isShowCoverLetter() ? f.coverLetterMessage() : null, f.termsAccepted(), null,
                f.answers());
        Candidate c = candidates.register(p, form, resume, coverLetter);
        return new Applied(p.getTitle(), c.getApplicationDate());
    }

    // ------------------------------------------------------------------ helpers

    private record Lookups(Map<Long, JobLocation> locations, Map<Long, String> types) {
    }

    private Lookups lookups(Collection<JobPosting> list) {
        return new Lookups(
                locations.findAllById(list.stream().map(JobPosting::getLocationId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(JobLocation::getId, Function.identity())),
                catalogItems.findAllById(list.stream().map(JobPosting::getJobTypeId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(CatalogItem::getId, CatalogItem::getName)));
    }

    private static JobCard card(JobPosting p, Lookups l) {
        JobLocation loc = l.locations().get(p.getLocationId());
        return new JobCard(p.getId().toString(), p.getTitle(), List.copyOf(p.getSkills()), p.getBranchName(),
                loc == null ? null : location(loc), new Ref(p.getJobTypeId().toString(), l.types().get(p.getJobTypeId())),
                p.getPositions(), p.isFeatured(), p.getPriority(), p.getMinSalary(), p.getMaxSalary(),
                p.getPublishDate(), p.getApplicationDeadline());
    }

    private static LocationRef location(JobLocation l) {
        return new LocationRef(l.getId().toString(), l.getName(), l.getCity(), l.isRemote());
    }

    private List<Ref> options(Long companyId, ECatalogKind kind) {
        return catalogItems.findByCompanyIdAndKindAndStatusAndDeletedFalseOrderByNameAsc(companyId, kind,
                EStatus.ACTIVE).stream().map(c -> new Ref(c.getId().toString(), c.getName())).toList();
    }

    /** A posting of this company that is open today; anything else is "not open" (404). */
    private JobPosting open(CareerSettings s, Long id) {
        LocalDate today = today();
        return postings.findByIdAndDeletedFalse(id)
                .filter(p -> p.getCompanyId().equals(s.getCompanyId()) && p.isPublished()
                        && p.getStatus() == JobPosting.EStatus.PUBLISHED
                        && (p.getApplicationDeadline() == null || !p.getApplicationDeadline().isBefore(today)))
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_048));
    }

    private CareerSettings require(String slug) {
        String key = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        return settings.findBySlug(key).filter(CareerSettings::isEnabled)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_047));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
