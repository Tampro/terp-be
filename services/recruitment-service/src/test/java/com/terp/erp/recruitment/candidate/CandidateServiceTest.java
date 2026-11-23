package com.terp.erp.recruitment.candidate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.CandidateDtos.AnswerForm;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateForm;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.config.RecruitmentProperties;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.location.JobLocationService;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;
import com.terp.erp.recruitment.question.CustomQuestion;
import com.terp.erp.recruitment.question.CustomQuestionRepository;
import com.terp.erp.recruitment.question.CustomQuestionService;
import com.terp.erp.recruitment.storage.FileStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidateServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final CatalogItemRepository items = mock(CatalogItemRepository.class);
    private final JobLocationRepository locations = mock(JobLocationRepository.class);
    private final CustomQuestionRepository questions = mock(CustomQuestionRepository.class);
    private final OrgClient org = mock(OrgClient.class);
    /** 2026-11-19 17:30 UTC = 2026-11-20 in Vietnam. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ZoneOffset.UTC);
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews = mock(com.terp.erp.recruitment.interview.InterviewRepository.class);
    private final CatalogService catalog = new CatalogService(items, postings, candidates, interviews);
    private final JobPostingService postingService = new JobPostingService(postings, catalog, items,
            new JobLocationService(locations, postings), locations, new CustomQuestionService(questions), questions,
            org, clock, candidates, mock(com.terp.erp.outbox.OutboxPublisher.class));

    @TempDir
    Path dir;

    private CandidateService service() {
        FileStore files = new FileStore(new RecruitmentProperties(null, null, dir.toString()));
        return new CandidateService(candidates, postings, postingService, catalog, items, locations, questions, org,
                files, clock, interviews, mock(com.terp.erp.recruitment.offer.OfferRepository.class),
                mock(com.terp.erp.recruitment.assessment.CandidateAssessmentRepository.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createChecksAnswersAndDuplicates() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubPosting(10L);
        when(candidates.save(any())).thenAnswer(i -> {
            Candidate c = i.getArgument(0);
            c.setId(9L);
            return c;
        });
        CandidateService s = service();

        assertCode(() -> s.create(form(List.of())), ERecruitmentError.REC_017);
        assertCode(() -> s.create(form(List.of(new AnswerForm(100L, "Yes"), new AnswerForm(300L, "x")))),
                ERecruitmentError.REC_018);

        var d = s.create(form(List.of(new AnswerForm(100L, " Yes "), new AnswerForm(200L, " "))));
        assertThat(d.email()).isEqualTo("test.candidate@example.invalid");
        assertThat(d.status()).isEqualTo(Candidate.EStatus.NEW);
        assertThat(d.applicationDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(d.answers()).singleElement().satisfies(a -> {
            assertThat(a.question()).isEqualTo("Why us?");
            assertThat(a.answer()).isEqualTo("Yes");
        });
        assertThat(d.resumeUrl()).isNull();
        ArgumentCaptor<Candidate> saved = ArgumentCaptor.forClass(Candidate.class);
        Mockito.verify(candidates).save(saved.capture());
        assertThat(saved.getValue().getTrackingId()).matches("[A-HJ-NP-Z2-9]{12}");

        when(candidates.alreadyApplied(1L, "test.candidate@example.invalid")).thenReturn(true);
        assertCode(() -> s.create(form(List.of(new AnswerForm(100L, "Yes")))), ERecruitmentError.REC_016);
    }

    @Test
    void answersFollowTheQuestionType() {
        CustomQuestion n = typed(CustomQuestion.EType.NUMBER);
        assertThat(CandidateService.checkAnswer(n, " 3.5 ")).isEqualTo("3.5");
        assertCode(() -> CandidateService.checkAnswer(n, "three"), ERecruitmentError.REC_064);
        CustomQuestion d = typed(CustomQuestion.EType.DATE);
        assertThat(CandidateService.checkAnswer(d, "2026-11-20")).isEqualTo("2026-11-20");
        assertCode(() -> CandidateService.checkAnswer(d, "20/11/2026"), ERecruitmentError.REC_065);
        CustomQuestion r = typed(CustomQuestion.EType.RADIO, "Yes", "No");
        assertThat(CandidateService.checkAnswer(r, "yes")).isEqualTo("Yes");
        assertCode(() -> CandidateService.checkAnswer(r, "Maybe"), ERecruitmentError.REC_066);
        CustomQuestion c = typed(CustomQuestion.EType.CHECKBOX, "Java", "Go", "PHP");
        assertThat(CandidateService.checkAnswer(c, "[\"java\", \"PHP\"]")).isEqualTo("Java, PHP");
        assertThat(CandidateService.checkAnswer(c, "Go, Java, Go")).isEqualTo("Go, Java");
        assertCode(() -> CandidateService.checkAnswer(c, "Go, Rust"), ERecruitmentError.REC_066);
        CustomQuestion t = typed(CustomQuestion.EType.TEXTAREA);
        assertThat(CandidateService.checkAnswer(t, "anything")).isEqualTo("anything");
    }

    private static CustomQuestion typed(CustomQuestion.EType type, String... options) {
        CustomQuestion q = question(1L, "Q", false);
        q.setType(type);
        q.getOptions().addAll(List.of(options));
        return q;
    }

    @Test
    void sourceMustBeACandidateSource() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubPosting(10L);
        // id 5 exists only as a JOB_TYPE: the lookup by kind CANDIDATE_SOURCE finds nothing
        CandidateForm f = new CandidateForm(1L, 5L, "Test", "Candidate", "a@example.invalid", null, null, null, 0,
                null, null, null, null, null, null, null, null, null, null, null, true, null, List.of());
        assertCode(() -> service().create(f), ERecruitmentError.REC_001);
    }

    @Test
    void otherCompanyIsNotFound() {
        TestLogin.as(11L, "RECRUITMENT_VIEW");
        Candidate c = new Candidate();
        c.setId(9L);
        c.setCompanyId(10L);
        when(candidates.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(c));
        assertCode(() -> service().get(9L), ERecruitmentError.REC_015);
        assertCode(() -> service().resume(9L), ERecruitmentError.REC_015);
    }

    @Test
    void resumeUploadReplacesAndChecksContent() throws Exception {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubPosting(10L);
        Candidate c = new Candidate();
        c.setId(9L);
        c.setCompanyId(10L);
        c.setJobPostingId(1L);
        when(candidates.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(c));
        CandidateService s = service();

        assertCode(() -> s.resume(9L), ERecruitmentError.REC_021);
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);
        assertCode(() -> s.uploadResume(9L, new MockMultipartFile("file", "cv.exe", "application/pdf", pdf)),
                ERecruitmentError.REC_019);
        assertCode(() -> s.uploadResume(9L, new MockMultipartFile("file", "cv.pdf", "application/pdf",
                "MZ not a pdf".getBytes(StandardCharsets.US_ASCII))), ERecruitmentError.REC_019);

        var first = s.uploadResume(9L, new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf));
        String firstKey = c.getResumeKey();
        assertThat(first.resumeUrl()).isEqualTo("/api/v1/recruitment/candidates/9/resume");
        assertThat(Files.exists(dir.resolve(firstKey))).isTrue();

        s.uploadResume(9L, new MockMultipartFile("file", "cv2.pdf", "application/pdf", pdf));
        assertThat(c.getResumeKey()).isNotEqualTo(firstKey);
        assertThat(Files.exists(dir.resolve(firstKey))).isFalse();
        assertThat(s.resume(9L).name()).isEqualTo("cv2.pdf");
    }

    @Test
    void statusCountsFillMissingStages() {
        TestLogin.as(10L, "RECRUITMENT_VIEW");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{Candidate.EStatus.NEW, 3L});
        rows.add(new Object[]{Candidate.EStatus.HIRED, 1L});
        when(candidates.countByStatus(10L, null, null, "")).thenReturn(rows);
        var counts = service().statusCounts(null, null, null, null);
        assertThat(counts.all()).isEqualTo(4);
        assertThat(counts.byStatus()).hasSize(6).containsEntry(Candidate.EStatus.SCREENING, 0L)
                .containsEntry(Candidate.EStatus.NEW, 3L);
    }

    @Test
    void sourceOrPostingInUseCannotBeDeleted() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubPosting(10L);
        CatalogItem source = new CatalogItem();
        source.setId(6L);
        source.setCompanyId(10L);
        source.setKind(ECatalogKind.CANDIDATE_SOURCE);
        when(items.findByIdAndKindAndDeletedFalse(6L, ECatalogKind.CANDIDATE_SOURCE)).thenReturn(Optional.of(source));
        when(candidates.existsBySourceIdAndDeletedFalse(6L)).thenReturn(true);
        when(candidates.existsByJobPostingIdAndDeletedFalse(1L)).thenReturn(true);
        assertCode(() -> catalog.delete(ECatalogKind.CANDIDATE_SOURCE, 6L), ERecruitmentError.REC_013);
        assertCode(() -> postingService.delete(1L), ERecruitmentError.REC_013);
    }

    private void stubPosting(Long companyId) {
        JobPosting p = new JobPosting();
        p.setId(1L);
        p.setCompanyId(companyId);
        p.setJobCode("JOB-0001");
        p.setTitle("Developer");
        p.setJobTypeId(2L);
        p.setLocationId(3L);
        p.setCustomQuestionIds(new java.util.HashSet<>(List.of(100L, 200L)));
        when(postings.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(p));
        when(postings.findById(1L)).thenReturn(Optional.of(p));
        when(questions.findAllById(any())).thenReturn(List.of(question(100L, "Why us?", true),
                question(200L, "Hobbies?", false)));
    }

    private static CustomQuestion question(Long id, String text, boolean required) {
        CustomQuestion q = new CustomQuestion();
        q.setId(id);
        q.setQuestion(text);
        q.setRequired(required);
        return q;
    }

    private static CandidateForm form(List<AnswerForm> answers) {
        return new CandidateForm(1L, null, " Test ", "Candidate", "Test.Candidate@Example.invalid", null, null, null,
                2, null, null, null, null, null, null, null, null, null, null, null, true, null, answers);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
