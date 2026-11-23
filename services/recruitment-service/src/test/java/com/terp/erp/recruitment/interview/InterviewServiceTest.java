package com.terp.erp.recruitment.interview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
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
import com.terp.erp.recruitment.interview.InterviewDtos.FeedbackForm;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewForm;
import com.terp.erp.recruitment.interview.InterviewFeedback.ERecommendation;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.round.InterviewRound;
import com.terp.erp.recruitment.round.InterviewRoundRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    private final InterviewRepository interviews = mock(InterviewRepository.class);
    private final InterviewFeedbackRepository feedbacks = mock(InterviewFeedbackRepository.class);
    private final CandidateService candidateService = mock(CandidateService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final InterviewRoundRepository rounds = mock(InterviewRoundRepository.class);
    private final CatalogItemRepository items = mock(CatalogItemRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final InterviewService service = new InterviewService(interviews, feedbacks, candidateService, candidates,
            postings, rounds, new CatalogService(items, postings, candidates, interviews), items, employees, outbox);

    private Candidate candidate;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCompanyId(10L);
        candidate.setJobPostingId(1L);
        candidate.setFirstName("Test");
        candidate.setLastName("Applicant");
        when(candidateService.require(7L)).thenReturn(candidate);
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate));
        when(rounds.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(round(2L, 1L)));
        when(rounds.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(round(3L, 99L)));
        CatalogItem type = new CatalogItem();
        type.setId(4L);
        type.setCompanyId(10L);
        type.setKind(ECatalogKind.INTERVIEW_TYPE);
        type.setStatus(EStatus.ACTIVE);
        when(items.findByIdAndKindAndDeletedFalse(4L, ECatalogKind.INTERVIEW_TYPE)).thenReturn(Optional.of(type));
        when(employees.employee(50L)).thenReturn(new EmployeeClient.Employee("50", "10", "Test Interviewer",
                "interviewer@example.invalid", new EmployeeClient.Ref("1", "Tech Lead")));
        when(employees.employee(51L)).thenReturn(new EmployeeClient.Employee("51", "10", "Second Interviewer",
                null, null));
        when(interviews.save(any())).thenAnswer(i -> {
            Interview x = i.getArgument(0);
            x.setId(100L);
            return x;
        });
        when(interviews.scheduledOn(10L, DAY)).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scheduleCopiesInterviewersAndMovesTheCandidate() {
        var d = service.create(form(2L, "10:00", 60, List.of(50L, 51L, 50L)));
        assertThat(d.status()).isEqualTo(Interview.EStatus.SCHEDULED);
        assertThat(d.interviewers()).extracting(InterviewDtos.InterviewerDto::name)
                .containsExactly("Test Interviewer", "Second Interviewer");
        assertThat(d.interviewers().get(0).designation()).isEqualTo("Tech Lead");
        assertThat(d.feedbackSummary().count()).isZero();
        assertThat(d.feedbackSummary().overall()).isNull();
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.INTERVIEW);
        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("recruitment.interview.scheduled"),
                org.mockito.ArgumentMatchers.eq("INTERVIEW"), org.mockito.ArgumentMatchers.eq(100L), payload.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) payload.getValue();
        assertThat(m).containsKeys("interviewId", "title", "candidateId", "candidateName", "jobPostingId", "jobTitle",
                        "date", "startTime", "durationMinutes", "location", "meetingLink")
                .containsEntry("recipientEmail", candidate.getEmail())
                .doesNotContainKey("interviewers");
    }

    @Test
    void scheduleRules() {
        assertCode(() -> service.create(form(3L, "10:00", 60, List.of(50L))), ERecruitmentError.REC_024);
        candidate.setStatus(Candidate.EStatus.OFFER);
        service.create(form(2L, "10:00", 60, List.of(50L)));
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.OFFER);
        candidate.setStatus(Candidate.EStatus.REJECTED);
        assertCode(() -> service.create(form(2L, "10:00", 60, List.of(50L))), ERecruitmentError.REC_025);
        verify(employees, never()).employee(51L);
    }

    @Test
    void conflictsWithScheduledInterviews() {
        Interview other = existing(200L, 8L, "10:30", 60, 50L);
        when(interviews.scheduledOn(10L, DAY)).thenReturn(List.of(other));
        assertThatThrownBy(() -> service.create(form(2L, "10:00", 60, List.of(50L))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ERecruitmentError.REC_026);
                    assertThat(((ApiException) e).getArgs()).containsExactly("Test Interviewer");
                });
        // ends exactly when the other starts: no overlap; another interviewer at the same time is fine
        service.create(form(2L, "09:30", 60, List.of(50L)));
        service.create(form(2L, "10:30", 30, List.of(51L)));
        other.setCandidateId(7L);
        assertCode(() -> service.create(form(2L, "10:45", 15, List.of(51L))), ERecruitmentError.REC_027);
    }

    @Test
    void backToScheduledChecksConflictsAgain() {
        Interview mine = existing(100L, 7L, "10:00", 60, 50L);
        mine.setStatus(Interview.EStatus.CANCELLED);
        when(interviews.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(mine));
        when(interviews.scheduledOn(10L, DAY)).thenReturn(List.of(existing(200L, 8L, "10:00", 30, 50L)));
        assertCode(() -> service.changeStatus(100L, Interview.EStatus.SCHEDULED), ERecruitmentError.REC_026);
        assertThat(service.changeStatus(100L, Interview.EStatus.COMPLETED).status())
                .isEqualTo(Interview.EStatus.COMPLETED);
    }

    @Test
    void completingMovesAnInterviewCandidateToOfferOnly() {
        Interview i = existing(100L, 7L, "10:00", 60, 50L);
        when(interviews.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(i));
        candidate.setStatus(Candidate.EStatus.INTERVIEW);
        service.changeStatus(100L, Interview.EStatus.COMPLETED);
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.OFFER);
        for (Candidate.EStatus s : List.of(Candidate.EStatus.NEW, Candidate.EStatus.SCREENING,
                Candidate.EStatus.HIRED, Candidate.EStatus.REJECTED)) {
            i.setStatus(Interview.EStatus.SCHEDULED);
            candidate.setStatus(s);
            service.changeStatus(100L, Interview.EStatus.COMPLETED);
            assertThat(candidate.getStatus()).isEqualTo(s);
        }
        // cancelling does not move the candidate
        i.setStatus(Interview.EStatus.SCHEDULED);
        candidate.setStatus(Candidate.EStatus.INTERVIEW);
        service.changeStatus(100L, Interview.EStatus.CANCELLED);
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.INTERVIEW);
    }

    @Test
    void feedbackRules() {
        Interview i = existing(100L, 7L, "10:00", 60, 50L);
        when(interviews.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(i));
        assertCode(() -> service.addFeedback(100L, feedback(50L, 4)), ERecruitmentError.REC_028);
        i.setStatus(Interview.EStatus.COMPLETED);
        assertCode(() -> service.addFeedback(100L, feedback(51L, 4)), ERecruitmentError.REC_029);
        when(feedbacks.existsByInterviewIdAndEmployeeId(100L, 50L)).thenReturn(true);
        assertCode(() -> service.addFeedback(100L, feedback(50L, 4)), ERecruitmentError.REC_030);
    }

    @Test
    void otherCompanyInterviewIsNotFound() {
        Interview i = existing(100L, 7L, "10:00", 60, 50L);
        i.setCompanyId(11L);
        when(interviews.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(i));
        assertCode(() -> service.get(100L), ERecruitmentError.REC_023);
    }

    @Test
    void summaryAveragesAndRecommendations() {
        InterviewFeedback a = fb(5, 4, ERecommendation.STRONG_HIRE);
        InterviewFeedback b = fb(4, 3, ERecommendation.HIRE);
        InterviewFeedback c = fb(4, 3, ERecommendation.HIRE);
        var s = InterviewService.summary(List.of(a, b, c));
        assertThat(s.count()).isEqualTo(3);
        assertThat(s.overall()).isEqualTo(4.3);
        assertThat(s.technical()).isEqualTo(3.3);
        assertThat(s.recommendations()).hasSize(5).containsEntry(ERecommendation.HIRE, 2L)
                .containsEntry(ERecommendation.REJECT, 0L);
    }

    private static InterviewFeedback fb(int overall, int technical, ERecommendation r) {
        InterviewFeedback f = new InterviewFeedback();
        f.setOverallScore(overall);
        f.setTechnicalScore(technical);
        f.setCommunicationScore(4);
        f.setCulturalFitScore(4);
        f.setRecommendation(r);
        return f;
    }

    private Interview existing(Long id, Long candidateId, String start, int minutes, Long employeeId) {
        Interview i = new Interview();
        i.setId(id);
        i.setCompanyId(10L);
        i.setCandidateId(candidateId);
        i.setJobPostingId(1L);
        i.setRoundId(2L);
        i.setInterviewTypeId(4L);
        i.setInterviewDate(DAY);
        i.setStartTime(LocalTime.parse(start));
        i.setDurationMinutes(minutes);
        i.getInterviewers().add(new Interview.Interviewer(employeeId, "Test Interviewer", null, null));
        return i;
    }

    private static InterviewRound round(Long id, Long postingId) {
        InterviewRound r = new InterviewRound();
        r.setId(id);
        r.setCompanyId(10L);
        r.setJobPostingId(postingId);
        r.setName("Screening");
        return r;
    }

    private static InterviewForm form(Long roundId, String start, int minutes, List<Long> interviewers) {
        return new InterviewForm(7L, roundId, 4L, DAY, LocalTime.parse(start), minutes, "Room A", null, interviewers);
    }

    private static FeedbackForm feedback(Long employeeId, int score) {
        return new FeedbackForm(employeeId, score, score, score, score, ERecommendation.HIRE, "Clear", null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }

    @Test
    void myInterviewsAreThoseOfMyEmployeeInMyCompany() {
        TestLogin.as(10L);
        when(employees.me()).thenReturn(new EmployeeClient.Employee("21", "10", "Panel Person", null, null));
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(interviews.ofInterviewer(10L, 21L, from, page))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), page, 0));

        assertThat(service.mine(from, page).getTotalElements()).isZero();
        verify(interviews).ofInterviewer(10L, 21L, from, page);
    }

    @Test
    void myInterviewsNeedAProfileOfMyCompany() {
        TestLogin.as(10L);
        when(employees.me()).thenReturn(new EmployeeClient.Employee("21", "11", "Elsewhere", null, null));
        assertThatThrownBy(() -> service.mine(null, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ERecruitmentError.REC_069));
        verify(interviews, never()).ofInterviewer(any(), any(), any(), any());
    }
}
