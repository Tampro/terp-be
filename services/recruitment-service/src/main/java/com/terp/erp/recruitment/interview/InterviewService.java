package com.terp.erp.recruitment.interview;

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
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.recruitment.interview.InterviewDtos.CandidatePanel;
import com.terp.erp.recruitment.interview.InterviewDtos.CandidateRef;
import com.terp.erp.recruitment.interview.InterviewDtos.FeedbackDto;
import com.terp.erp.recruitment.interview.InterviewDtos.FeedbackForm;
import com.terp.erp.recruitment.interview.InterviewDtos.FeedbackSummary;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewDetail;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewForm;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewRow;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewSummary;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewerDto;
import com.terp.erp.recruitment.interview.InterviewDtos.JobRef;
import com.terp.erp.recruitment.interview.InterviewDtos.KanbanColumn;
import com.terp.erp.recruitment.interview.InterviewDtos.Ref;
import com.terp.erp.recruitment.interview.InterviewFeedback.ERecommendation;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.round.InterviewRound;
import com.terp.erp.recruitment.round.InterviewRoundRepository;
import com.terp.erp.security.TenantGuard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Interviews page, calendar and kanban (docs/analysis/06 §7.1.3), decided with the PO on 2026-11-20:
 * interviewers are employees of employee-service (name / email / designation copied when assigned); anyone with
 * RECRUITMENT_MANAGE records feedback on behalf of one of the interview's interviewers, at most one each; scheduling
 * moves a NEW / SCREENING candidate to INTERVIEW and refuses a HIRED / REJECTED one; a scheduled interview may not
 * overlap another scheduled interview of the same interviewer (REC_026) or candidate (REC_027).
 * TODO(assumption): feedback only once the interview is COMPLETED (REC_028) — the spec's "Pending Feedback" counts
 * completed interviews without feedback; confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class InterviewService {
    static final int KANBAN_CARDS = 50;

    private final InterviewRepository interviews;
    private final InterviewFeedbackRepository feedbacks;
    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final JobPostingRepository postings;
    private final InterviewRoundRepository rounds;
    private final CatalogService catalog;
    private final CatalogItemRepository catalogItems;
    private final EmployeeClient employees;

    // ------------------------------------------------------------------ reads

    /** Calendar and day table: from / to bound the dates (both inclusive). */
    @Transactional(readOnly = true)
    public Page<InterviewRow> search(Long requestedCompanyId, String keyword, Long candidateId, LocalDate from,
                                     LocalDate to, Interview.EStatus status, Pageable pageable) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Page<Interview> page = interviews.search(companyId, candidateId, from, to, status, Texts.kw(keyword), pageable);
        Lookups l = lookups(page.getContent());
        return page.map(i -> row(i, l));
    }

    @Transactional(readOnly = true)
    public InterviewSummary summary(Long requestedCompanyId, String keyword, Long candidateId, LocalDate from,
                                    LocalDate to) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Map<Interview.EStatus, Long> by = counts(companyId, keyword, candidateId, from, to);
        return new InterviewSummary(by.values().stream().mapToLong(Long::longValue).sum(), by,
                interviews.countPendingFeedback(companyId, candidateId, from, to, Texts.kw(keyword)));
    }

    /** Four columns in status order, each with its count and the next 50 cards by date and time. */
    @Transactional(readOnly = true)
    public List<KanbanColumn> kanban(Long requestedCompanyId, String keyword, Long candidateId, LocalDate from,
                                     LocalDate to) {
        Long companyId = CatalogService.company(requestedCompanyId);
        Map<Interview.EStatus, Long> by = counts(companyId, keyword, candidateId, from, to);
        List<KanbanColumn> out = new ArrayList<>();
        for (Interview.EStatus s : Interview.EStatus.values()) {
            List<Interview> cards = interviews.search(companyId, candidateId, from, to, s, Texts.kw(keyword),
                    PageRequest.of(0, KANBAN_CARDS, Sort.by("interviewDate", "startTime"))).getContent();
            Lookups l = lookups(cards);
            out.add(new KanbanColumn(s, by.get(s), cards.stream().map(i -> row(i, l)).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public InterviewDetail get(Long id) {
        return detail(require(id));
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public InterviewDetail create(InterviewForm form) {
        Interview i = new Interview();
        apply(i, form);
        interviews.save(i);
        return detail(i);
    }

    /** Full replacement of the form fields; the status is kept. */
    @Transactional
    public InterviewDetail update(Long id, InterviewForm form) {
        Interview i = require(id);
        apply(i, form);
        return detail(i);
    }

    @Transactional
    public InterviewDetail changeStatus(Long id, Interview.EStatus status) {
        Interview i = require(id);
        if (status == Interview.EStatus.SCHEDULED && i.getStatus() != Interview.EStatus.SCHEDULED) {
            checkConflicts(i);
        }
        i.setStatus(status);
        return detail(i);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    @Transactional
    public InterviewDetail addFeedback(Long interviewId, FeedbackForm form) {
        Interview i = require(interviewId);
        if (i.getStatus() != Interview.EStatus.COMPLETED) {
            throw ApiException.badRequest(ERecruitmentError.REC_028);
        }
        Interview.Interviewer who = i.getInterviewers().stream()
                .filter(x -> x.getEmployeeId().equals(form.employeeId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ERecruitmentError.REC_029));
        if (feedbacks.existsByInterviewIdAndEmployeeId(i.getId(), who.getEmployeeId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_030);
        }
        InterviewFeedback f = new InterviewFeedback();
        f.setCompanyId(i.getCompanyId());
        f.setInterviewId(i.getId());
        f.setEmployeeId(who.getEmployeeId());
        f.setInterviewerName(who.getName());
        f.setInterviewerRole(who.getDesignation());
        scores(f, form);
        feedbacks.save(f);
        return detail(i);
    }

    /** Full replacement of the scores and texts; the interviewer cannot change. */
    @Transactional
    public InterviewDetail updateFeedback(Long interviewId, Long feedbackId, FeedbackForm form) {
        Interview i = require(interviewId);
        InterviewFeedback f = requireFeedback(i, feedbackId);
        if (!f.getEmployeeId().equals(form.employeeId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_029);
        }
        scores(f, form);
        return detail(i);
    }

    @Transactional
    public InterviewDetail deleteFeedback(Long interviewId, Long feedbackId) {
        Interview i = require(interviewId);
        feedbacks.delete(requireFeedback(i, feedbackId));
        feedbacks.flush();
        return detail(i);
    }

    private void apply(Interview i, InterviewForm f) {
        Candidate c = candidateService.require(f.candidateId());
        boolean newCandidate = !Objects.equals(c.getId(), i.getCandidateId());
        if (newCandidate && (c.getStatus() == Candidate.EStatus.HIRED
                || c.getStatus() == Candidate.EStatus.REJECTED)) {
            throw ApiException.badRequest(ERecruitmentError.REC_025);
        }
        InterviewRound round = rounds.findByIdAndDeletedFalse(f.roundId())
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_007));
        TenantGuard.assertSameCompany(round.getCompanyId(), ERecruitmentError.REC_007);
        if (!round.getJobPostingId().equals(c.getJobPostingId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_024);
        }
        if (round.getStatus() != EStatus.ACTIVE && !Objects.equals(round.getId(), i.getRoundId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_014, "interview round");
        }
        CatalogItem type = catalog.require(ECatalogKind.INTERVIEW_TYPE, f.interviewTypeId());
        if (type.getStatus() != EStatus.ACTIVE && !Objects.equals(type.getId(), i.getInterviewTypeId())) {
            throw ApiException.badRequest(ERecruitmentError.REC_014, "interview type");
        }
        Map<Long, Interview.Interviewer> kept = i.getInterviewers().stream()
                .collect(Collectors.toMap(Interview.Interviewer::getEmployeeId, Function.identity(), (a, b) -> a));
        List<Interview.Interviewer> assigned = new ArrayList<>();
        for (Long employeeId : new LinkedHashSet<>(f.interviewerIds())) {
            Interview.Interviewer known = kept.get(employeeId);
            if (known != null) {
                assigned.add(known);
            } else {
                EmployeeClient.Employee e = employees.employee(employeeId);
                assigned.add(new Interview.Interviewer(employeeId, e.fullName(), e.email(), e.designationName()));
            }
        }
        i.setCompanyId(c.getCompanyId());
        i.setCandidateId(c.getId());
        i.setJobPostingId(c.getJobPostingId());
        i.setRoundId(round.getId());
        i.setInterviewTypeId(type.getId());
        i.setInterviewDate(f.date());
        i.setStartTime(f.startTime().withSecond(0).withNano(0));
        i.setDurationMinutes(f.durationMinutes());
        i.setLocation(Texts.blankToNull(f.location()));
        i.setMeetingLink(Texts.blankToNull(f.meetingLink()));
        i.getInterviewers().clear();
        i.getInterviewers().addAll(assigned);
        if (i.getStatus() == Interview.EStatus.SCHEDULED) {
            checkConflicts(i);
        }
        if (newCandidate && (c.getStatus() == Candidate.EStatus.NEW || c.getStatus() == Candidate.EStatus.SCREENING)) {
            c.setStatus(Candidate.EStatus.INTERVIEW);
        }
    }

    /** Against the other scheduled interviews of the company that day. */
    private void checkConflicts(Interview i) {
        Set<Long> mine = i.getInterviewers().stream().map(Interview.Interviewer::getEmployeeId)
                .collect(Collectors.toSet());
        for (Interview o : interviews.scheduledOn(i.getCompanyId(), i.getInterviewDate())) {
            if (Objects.equals(o.getId(), i.getId()) || !i.overlaps(o)) {
                continue;
            }
            if (o.getCandidateId().equals(i.getCandidateId())) {
                throw ApiException.badRequest(ERecruitmentError.REC_027);
            }
            for (Interview.Interviewer x : o.getInterviewers()) {
                if (mine.contains(x.getEmployeeId())) {
                    String name = i.getInterviewers().stream().filter(y -> y.getEmployeeId().equals(x.getEmployeeId()))
                            .map(Interview.Interviewer::getName).filter(Objects::nonNull).findFirst()
                            .orElse(x.getEmployeeId().toString());
                    throw ApiException.badRequest(ERecruitmentError.REC_026, name);
                }
            }
        }
    }

    private static void scores(InterviewFeedback f, FeedbackForm form) {
        f.setOverallScore(form.overallScore());
        f.setTechnicalScore(form.technicalScore());
        f.setCommunicationScore(form.communicationScore());
        f.setCulturalFitScore(form.culturalFitScore());
        f.setRecommendation(form.recommendation());
        f.setStrengths(Texts.blankToNull(form.strengths()));
        f.setWeaknesses(Texts.blankToNull(form.weaknesses()));
        f.setComments(Texts.blankToNull(form.comments()));
    }

    // ------------------------------------------------------------------ mapping

    private record Lookups(Map<Long, Candidate> candidates, Map<Long, String> jobTitles, Map<Long, String> rounds,
                           Map<Long, String> types, Set<Long> withFeedback) {
    }

    private Lookups lookups(Collection<Interview> list) {
        Set<Long> ids = list.stream().map(Interview::getId).collect(Collectors.toSet());
        return new Lookups(
                candidates.findAllById(list.stream().map(Interview::getCandidateId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(Candidate::getId, Function.identity())),
                postings.findAllById(list.stream().map(Interview::getJobPostingId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(JobPosting::getId, JobPosting::getTitle)),
                rounds.findAllById(list.stream().map(Interview::getRoundId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(InterviewRound::getId, InterviewRound::getName)),
                catalogItems.findAllById(list.stream().map(Interview::getInterviewTypeId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(CatalogItem::getId, CatalogItem::getName)),
                ids.isEmpty() ? Set.of() : new HashSet<>(feedbacks.interviewsWithFeedback(ids)));
    }

    private static InterviewRow row(Interview i, Lookups l) {
        Candidate c = l.candidates().get(i.getCandidateId());
        return new InterviewRow(i.getId().toString(),
                new CandidateRef(i.getCandidateId().toString(), c == null ? null : c.fullName(),
                        l.jobTitles().get(i.getJobPostingId())),
                new Ref(i.getRoundId().toString(), l.rounds().get(i.getRoundId())),
                new Ref(i.getInterviewTypeId().toString(), l.types().get(i.getInterviewTypeId())),
                i.getInterviewDate(), i.getStartTime(), i.getDurationMinutes(), i.getLocation(), i.getMeetingLink(),
                i.getStatus(), l.withFeedback().contains(i.getId()), interviewers(i));
    }

    private InterviewDetail detail(Interview i) {
        Candidate c = candidates.findById(i.getCandidateId()).orElse(null);
        JobPosting p = postings.findById(i.getJobPostingId()).orElse(null);
        List<InterviewFeedback> list = feedbacks.findByInterviewIdOrderByCreatedDateAsc(i.getId());
        CandidatePanel panel = c == null ? null : new CandidatePanel(c.getId().toString(), c.fullName(), c.getEmail(),
                c.getPhone(), c.getCurrentPosition(), c.getCurrentCompany(), c.getExperienceYears(),
                c.getNoticePeriod(), c.getStatus());
        JobRef job = p == null ? new JobRef(i.getJobPostingId().toString(), null, null)
                : new JobRef(p.getId().toString(), p.getJobCode(), p.getTitle());
        return new InterviewDetail(i.getId().toString(), panel, job,
                new Ref(i.getRoundId().toString(),
                        rounds.findById(i.getRoundId()).map(InterviewRound::getName).orElse(null)),
                new Ref(i.getInterviewTypeId().toString(),
                        catalogItems.findById(i.getInterviewTypeId()).map(CatalogItem::getName).orElse(null)),
                i.getInterviewDate(), i.getStartTime(), i.getDurationMinutes(), i.getLocation(), i.getMeetingLink(),
                i.getStatus(), !list.isEmpty(), interviewers(i), summary(list),
                list.stream().map(InterviewService::feedback).toList(), i.getCreatedDate(), i.getUpdatedDate());
    }

    static FeedbackSummary summary(List<InterviewFeedback> list) {
        Map<ERecommendation, Long> recs = new EnumMap<>(ERecommendation.class);
        for (ERecommendation r : ERecommendation.values()) {
            recs.put(r, 0L);
        }
        list.forEach(f -> recs.merge(f.getRecommendation(), 1L, Long::sum));
        return new FeedbackSummary(list.size(), avg(list, InterviewFeedback::getOverallScore),
                avg(list, InterviewFeedback::getTechnicalScore), avg(list, InterviewFeedback::getCommunicationScore),
                avg(list, InterviewFeedback::getCulturalFitScore), recs);
    }

    /** One decimal, null without feedback. */
    private static Double avg(List<InterviewFeedback> list, ToIntFunction<InterviewFeedback> score) {
        return list.isEmpty() ? null
                : Math.round(list.stream().mapToInt(score).average().orElse(0) * 10) / 10.0;
    }

    private static FeedbackDto feedback(InterviewFeedback f) {
        return new FeedbackDto(f.getId().toString(), f.getEmployeeId().toString(), f.getInterviewerName(),
                f.getInterviewerRole(), f.getOverallScore(), f.getTechnicalScore(), f.getCommunicationScore(),
                f.getCulturalFitScore(), f.getRecommendation(), f.getStrengths(), f.getWeaknesses(), f.getComments(),
                f.getCreatedDate(), f.getUpdatedDate());
    }

    private static List<InterviewerDto> interviewers(Interview i) {
        return i.getInterviewers().stream().map(x -> new InterviewerDto(x.getEmployeeId().toString(), x.getName(),
                x.getEmail(), x.getDesignation())).toList();
    }

    private Map<Interview.EStatus, Long> counts(Long companyId, String keyword, Long candidateId, LocalDate from,
                                                LocalDate to) {
        Map<Interview.EStatus, Long> by = new EnumMap<>(Interview.EStatus.class);
        for (Interview.EStatus s : Interview.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : interviews.countByStatus(companyId, candidateId, from, to, Texts.kw(keyword))) {
            by.put((Interview.EStatus) r[0], (Long) r[1]);
        }
        return by;
    }

    private InterviewFeedback requireFeedback(Interview i, Long feedbackId) {
        return feedbacks.findById(feedbackId).filter(f -> f.getInterviewId().equals(i.getId()))
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_031));
    }

    private Interview require(Long id) {
        Interview i = interviews.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_023));
        TenantGuard.assertSameCompany(i.getCompanyId(), ERecruitmentError.REC_023);
        return i;
    }
}
