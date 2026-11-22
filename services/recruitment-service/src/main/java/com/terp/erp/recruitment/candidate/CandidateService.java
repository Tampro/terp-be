package com.terp.erp.recruitment.candidate;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.candidate.Candidate.EStatus;
import com.terp.erp.recruitment.candidate.CandidateDtos.AnswerDto;
import com.terp.erp.recruitment.candidate.CandidateDtos.AnswerForm;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateDetail;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateForm;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateRow;
import com.terp.erp.recruitment.candidate.CandidateDtos.JobRef;
import com.terp.erp.recruitment.candidate.CandidateDtos.KanbanColumn;
import com.terp.erp.recruitment.candidate.CandidateDtos.Ref;
import com.terp.erp.recruitment.candidate.CandidateDtos.StatusCounts;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.location.JobLocation;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;
import com.terp.erp.recruitment.question.CustomQuestion;
import com.terp.erp.recruitment.question.CustomQuestionRepository;
import com.terp.erp.recruitment.storage.FileStore;
import com.terp.erp.recruitment.storage.FileStore.StoredFile;
import com.terp.erp.security.TenantGuard;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidates page and kanban (docs/analysis/06 §7.1.2). Admins add candidates by hand here (e.g. from LinkedIn); the
 * public career page will add them through {@link #register}. One application per email and posting (REC_016); the
 * posting's required custom questions must be answered (REC_017). The status moves freely between the six stages
 * (kanban drag and drop).
 * TODO(assumption): the spec has no admin "Add Candidate" form; the manual entry is a proposal for sources other than
 * the career page — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class CandidateService {
    static final int KANBAN_CARDS = 50;
    static final int TRACKING_LENGTH = 12;
    /** No 0/O/1/I so an applicant can read it back. */
    private static final char[] TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    /** TODO(assumption): "today" (application date) is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CandidateRepository candidates;
    private final JobPostingRepository postings;
    private final JobPostingService postingService;
    private final CatalogService catalog;
    private final CatalogItemRepository catalogItems;
    private final JobLocationRepository locations;
    private final CustomQuestionRepository questions;
    private final OrgClient org;
    private final FileStore files;
    private final Clock clock;
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews;
    private final com.terp.erp.recruitment.offer.OfferRepository offers;
    private final com.terp.erp.recruitment.assessment.CandidateAssessmentRepository assessments;

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<CandidateRow> search(Long requestedCompanyId, String keyword, Long jobPostingId, Long sourceId,
                                     EStatus status, Pageable pageable) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Page<Candidate> page = candidates.search(companyId, jobPostingId, sourceId, status, Texts.kw(keyword), pageable);
        Lookups l = lookups(page.getContent());
        return page.map(c -> row(c, l));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long jobPostingId, Long sourceId) {
        Map<EStatus, Long> by = counts(CatalogService.company(requestedCompanyId), keyword, jobPostingId, sourceId);
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Six columns in pipeline order, each with its count and the latest updated cards (at most 50). */
    @Transactional(readOnly = true)
    public List<KanbanColumn> kanban(Long requestedCompanyId, String keyword, Long jobPostingId, Long sourceId) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Map<EStatus, Long> by = counts(companyId, keyword, jobPostingId, sourceId);
        List<KanbanColumn> out = new ArrayList<>();
        for (EStatus s : EStatus.values()) {
            List<Candidate> cards = candidates.search(companyId, jobPostingId, sourceId, s, Texts.kw(keyword),
                    PageRequest.of(0, KANBAN_CARDS, Sort.by(Sort.Direction.DESC, "updatedDate"))).getContent();
            Lookups l = lookups(cards);
            out.add(new KanbanColumn(s, by.get(s), cards.stream().map(c -> row(c, l)).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public CandidateDetail get(Long id) {
        return detail(require(id));
    }

    /** The resume file and its name / type, for the download. */
    @Transactional(readOnly = true)
    public Resume resume(Long id) {
        Candidate c = require(id);
        if (c.getResumeKey() == null) {
            throw ApiException.notFound(ERecruitmentError.REC_021);
        }
        return new Resume(files.path(c.getResumeKey()), c.getResumeName(), c.getResumeMime());
    }

    /** The cover letter file sent with the application, for the download. */
    @Transactional(readOnly = true)
    public Resume coverLetter(Long id) {
        Candidate c = require(id);
        if (c.getCoverLetterKey() == null) {
            throw ApiException.notFound(ERecruitmentError.REC_053);
        }
        return new Resume(files.path(c.getCoverLetterKey()), c.getCoverLetterName(), c.getCoverLetterMime());
    }

    public record Resume(Path path, String name, String mimeType) {
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public CandidateDetail create(CandidateForm form) {
        JobPosting p = postingService.require(form.jobPostingId());
        if (form.sourceId() != null) {
            catalog.require(ECatalogKind.CANDIDATE_SOURCE, form.sourceId());
        }
        return detail(register(p, form, null, null));
    }

    /**
     * Saves an application to a posting already resolved for the company (admin form, or the public career page):
     * one per email and posting, the posting's required questions answered.
     */
    @Transactional
    public Candidate register(JobPosting p, CandidateForm f, MultipartFile resume, MultipartFile coverLetter) {
        String email = f.email().trim().toLowerCase(Locale.ROOT);
        if (candidates.alreadyApplied(p.getId(), email)) {
            throw ApiException.badRequest(ERecruitmentError.REC_016);
        }
        Candidate c = new Candidate();
        c.setCompanyId(p.getCompanyId());
        c.setJobPostingId(p.getId());
        c.setSourceId(f.sourceId());
        c.setFirstName(f.firstName().trim());
        c.setLastName(f.lastName().trim());
        c.setEmail(email);
        c.setPhone(Texts.blankToNull(f.phone()));
        c.setCurrentCompany(Texts.blankToNull(f.currentCompany()));
        c.setCurrentPosition(Texts.blankToNull(f.currentPosition()));
        c.setExperienceYears(f.experienceYears());
        c.setCurrentSalary(f.currentSalary());
        c.setExpectedSalary(f.expectedSalary());
        c.setNoticePeriod(Texts.blankToNull(f.noticePeriod()));
        c.setGender(f.gender());
        c.setDateOfBirth(f.dateOfBirth());
        c.setAddress(Texts.blankToNull(f.address()));
        c.setCity(Texts.blankToNull(f.city()));
        c.setState(Texts.blankToNull(f.state()));
        c.setZipCode(Texts.blankToNull(f.zipCode()));
        c.setCountry(Texts.blankToNull(f.country()));
        c.setCoverLetterMessage(Texts.blankToNull(f.coverLetterMessage()));
        c.setTermsAccepted(f.termsAccepted());
        c.setApplicationDate(f.applicationDate() != null ? f.applicationDate() : LocalDate.now(clock.withZone(ZONE)));
        c.setAnswers(answers(p, f.answers()));
        c.setTrackingId(newTrackingId());
        if (resume != null && !resume.isEmpty()) {
            attach(c, files.store(resume));
        }
        if (coverLetter != null && !coverLetter.isEmpty()) {
            StoredFile cl = files.store(coverLetter);
            c.setCoverLetterKey(cl.key());
            c.setCoverLetterName(cl.name());
            c.setCoverLetterMime(cl.mimeType());
            c.setCoverLetterSize(cl.size());
        }
        return candidates.save(c);
    }

    /** Replaces the resume. */
    @Transactional
    public CandidateDetail uploadResume(Long id, MultipartFile file) {
        Candidate c = require(id);
        String old = c.getResumeKey();
        attach(c, files.store(file));
        if (old != null) {
            files.delete(old);
        }
        return detail(c);
    }

    /** Any stage to any stage (kanban drag and drop). */
    @Transactional
    public CandidateDetail changeStatus(Long id, EStatus status) {
        Candidate c = require(id);
        c.setStatus(status);
        return detail(c);
    }

    /** Their interviews, offers and assessments go too; an onboarding (an employee by then) stays. */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
        interviews.deleteByCandidate(id);
        offers.deleteByCandidate(id);
        assessments.deleteByCandidate(id);
    }

    /** The posting's questions only; every required one answered; blank answers not kept. Question texts are copied. */
    private List<Candidate.Answer> answers(JobPosting p, List<AnswerForm> given) {
        Map<Long, String> byId = new HashMap<>();
        for (AnswerForm a : given == null ? List.<AnswerForm>of() : given) {
            if (!p.getCustomQuestionIds().contains(a.questionId())) {
                throw ApiException.badRequest(ERecruitmentError.REC_018);
            }
            byId.put(a.questionId(), Texts.blankToNull(a.answer()));
        }
        List<Candidate.Answer> out = new ArrayList<>();
        for (CustomQuestion q : questions.findAllById(p.getCustomQuestionIds())) {
            if (q.isDeleted()) {
                continue;
            }
            String answer = byId.get(q.getId());
            if (q.isRequired() && answer == null) {
                throw ApiException.badRequest(ERecruitmentError.REC_017, q.getQuestion());
            }
            if (answer != null) {
                answer = checkAnswer(q, answer);
                out.add(new Candidate.Answer(q.getId(), q.getQuestion(), answer));
            }
        }
        return out;
    }

    /**
     * The answer against the question type: NUMBER parses as a decimal (REC_064), DATE as yyyy-MM-dd (REC_065),
     * SELECT / RADIO is one option, CHECKBOX one or more options as a JSON array or comma separated (REC_066,
     * case-insensitive, stored with the option's own spelling, comma separated). TEXT / TEXTAREA as given.
     */
    static String checkAnswer(CustomQuestion q, String answer) {
        CustomQuestion.EType type = q.getType() == null ? CustomQuestion.EType.TEXT : q.getType();
        switch (type) {
            case NUMBER -> {
                try {
                    new java.math.BigDecimal(answer.trim());
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest(ERecruitmentError.REC_064, q.getQuestion());
                }
                return answer.trim();
            }
            case DATE -> {
                try {
                    return LocalDate.parse(answer.trim()).toString();
                } catch (java.time.format.DateTimeParseException e) {
                    throw ApiException.badRequest(ERecruitmentError.REC_065, q.getQuestion());
                }
            }
            case SELECT, RADIO -> {
                return option(q, answer.trim());
            }
            case CHECKBOX -> {
                List<String> picked = new ArrayList<>();
                for (String part : splitChoices(answer)) {
                    String o = option(q, part);
                    if (!picked.contains(o)) {
                        picked.add(o);
                    }
                }
                if (picked.isEmpty()) {
                    throw ApiException.badRequest(ERecruitmentError.REC_066, q.getQuestion());
                }
                return String.join(", ", picked);
            }
            default -> {
                return answer;
            }
        }
    }

    private static String option(CustomQuestion q, String value) {
        return q.getOptions().stream().filter(o -> o.equalsIgnoreCase(value)).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ERecruitmentError.REC_066, q.getQuestion()));
    }

    /** ["a","b"] or a, b. */
    private static List<String> splitChoices(String answer) {
        String s = answer.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
            List<String> out = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
            while (m.find()) {
                out.add(m.group(1).replace("\\\"", "\"").trim());
            }
            return out.stream().filter(x -> !x.isEmpty()).toList();
        }
        return java.util.Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** 32^12 values; a clash with an existing id (practically never) draws again. */
    private String newTrackingId() {
        while (true) {
            char[] out = new char[TRACKING_LENGTH];
            for (int i = 0; i < out.length; i++) {
                out[i] = TRACKING_ALPHABET[RANDOM.nextInt(TRACKING_ALPHABET.length)];
            }
            String id = new String(out);
            if (!candidates.existsByTrackingId(id)) {
                return id;
            }
        }
    }

    private static void attach(Candidate c, StoredFile f) {
        c.setResumeKey(f.key());
        c.setResumeName(f.name());
        c.setResumeMime(f.mimeType());
        c.setResumeSize(f.size());
    }

    // ------------------------------------------------------------------ mapping

    private record Lookups(Map<Long, JobPosting> postings, Map<Long, String> sources) {
    }

    private Lookups lookups(Collection<Candidate> list) {
        Set<Long> postingIds = list.stream().map(Candidate::getJobPostingId).collect(Collectors.toSet());
        Set<Long> sourceIds = new HashSet<>(list.stream().map(Candidate::getSourceId)
                .filter(java.util.Objects::nonNull).toList());
        return new Lookups(postings.findAllById(postingIds).stream()
                .collect(Collectors.toMap(JobPosting::getId, Function.identity())),
                catalogItems.findAllById(sourceIds).stream()
                        .collect(Collectors.toMap(CatalogItem::getId, CatalogItem::getName)));
    }

    private static CandidateRow row(Candidate c, Lookups l) {
        JobPosting p = l.postings().get(c.getJobPostingId());
        return new CandidateRow(c.getId().toString(), c.fullName(), c.getEmail(), job(c, p),
                c.getSourceId() == null ? null : new Ref(c.getSourceId().toString(), l.sources().get(c.getSourceId())),
                c.getExperienceYears(), c.getExpectedSalary(), c.getStatus(), c.getEmployeeId() != null,
                c.getApplicationDate(), c.getUpdatedDate());
    }

    private CandidateDetail detail(Candidate c) {
        JobPosting p = postings.findById(c.getJobPostingId()).orElse(null);
        Ref branch = p == null || p.getBranchId() == null ? null
                : new Ref(p.getBranchId().toString(), org.branchName(p.getBranchId()));
        Ref department = p == null || p.getDepartmentId() == null ? null
                : new Ref(p.getDepartmentId().toString(), org.departmentName(p.getDepartmentId()));
        Ref location = p == null ? null : new Ref(p.getLocationId().toString(),
                locations.findById(p.getLocationId()).map(JobLocation::getName).orElse(null));
        Ref jobType = p == null ? null : new Ref(p.getJobTypeId().toString(),
                catalogItems.findById(p.getJobTypeId()).map(CatalogItem::getName).orElse(null));
        Ref source = c.getSourceId() == null ? null : new Ref(c.getSourceId().toString(),
                catalogItems.findById(c.getSourceId()).map(CatalogItem::getName).orElse(null));
        return new CandidateDetail(c.getId().toString(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getCity(), c.getState(), c.getCountry(), c.getZipCode(), job(c, p), branch,
                location, jobType, department, source, c.getCurrentCompany(), c.getCurrentPosition(),
                c.getExperienceYears(), c.getCurrentSalary(), c.getExpectedSalary(), c.getNoticePeriod(),
                c.getGender(), c.getDateOfBirth(), c.getCoverLetterMessage(), c.getResumeName(), c.getResumeSize(),
                c.getResumeKey() == null ? null : "/api/v1/recruitment/candidates/" + c.getId() + "/resume",
                c.getCoverLetterName(),
                c.getCoverLetterKey() == null ? null
                        : "/api/v1/recruitment/candidates/" + c.getId() + "/cover-letter",
                c.getAnswers().stream().map(a -> new AnswerDto(a.getCustomQuestionId().toString(), a.getQuestion(),
                        a.getAnswer())).toList(), c.isTermsAccepted(), c.getStatus(),
                c.getEmployeeId() == null ? null : c.getEmployeeId().toString(), c.getApplicationDate(),
                c.getCreatedDate(), c.getUpdatedDate());
    }

    private static JobRef job(Candidate c, JobPosting p) {
        return p == null ? new JobRef(c.getJobPostingId().toString(), null, null)
                : new JobRef(p.getId().toString(), p.getJobCode(), p.getTitle());
    }

    private Map<EStatus, Long> counts(Long companyId, String keyword, Long jobPostingId, Long sourceId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : candidates.countByStatus(companyId, jobPostingId, sourceId, Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return by;
    }

    /** No tenant check: the public career page matches the company itself. */
    public java.util.Optional<Candidate> findByTracking(String trackingId) {
        return candidates.findByTrackingIdAndDeletedFalse(trackingId);
    }

    public Candidate require(Long id) {
        Candidate c = candidates.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_015));
        TenantGuard.assertSameCompany(c.getCompanyId(), ERecruitmentError.REC_015);
        return c;
    }
}
