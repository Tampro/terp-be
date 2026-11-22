package com.terp.erp.recruitment.career;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateForm;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.career.CareerSettingsService.SettingsForm;
import com.terp.erp.recruitment.career.PublicCareerService.ApplicationForm;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.question.CustomQuestionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCareerServiceTest {
    /** 2026-11-19 17:30 UTC = 2026-11-20 in Vietnam. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private final CareerSettingsRepository settings = mock(CareerSettingsRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final CatalogItemRepository items = mock(CatalogItemRepository.class);
    private final CandidateService candidates = mock(CandidateService.class);
    private final PublicCareerService service = new PublicCareerService(settings, postings,
            mock(JobLocationRepository.class), items, mock(CustomQuestionRepository.class), candidates,
            Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ZoneOffset.UTC));
    private final MockMultipartFile resume = new MockMultipartFile("resume", "cv.pdf", "application/pdf",
            "%PDF-1.4".getBytes());

    private JobPosting posting;

    @BeforeEach
    void setUp() {
        CareerSettings s = new CareerSettings();
        s.setCompanyId(10L);
        s.setSlug("test-co");
        s.setEnabled(true);
        s.setDisplayName("Test Co");
        when(settings.findBySlug("test-co")).thenReturn(Optional.of(s));
        CareerSettings off = new CareerSettings();
        off.setCompanyId(11L);
        off.setSlug("off-co");
        when(settings.findBySlug("off-co")).thenReturn(Optional.of(off));
        posting = new JobPosting();
        posting.setId(5L);
        posting.setCompanyId(10L);
        posting.setTitle("Developer");
        posting.setJobTypeId(2L);
        posting.setLocationId(3L);
        posting.setPublished(true);
        posting.setStatus(JobPosting.EStatus.PUBLISHED);
        posting.setApplicationDeadline(TODAY);
        when(postings.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(posting));
        when(candidates.register(any(), any(), any(), any())).thenAnswer(i -> {
            Candidate c = new Candidate();
            c.setApplicationDate(TODAY);
            c.setTrackingId("ABCDEFGH2345");
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledOrUnknownPageIsNotFound() {
        assertCode(() -> service.site("off-co"), ERecruitmentError.REC_047);
        assertCode(() -> service.site("nope"), ERecruitmentError.REC_047);
        when(postings.countOpen(10L, TODAY)).thenReturn(4L);
        assertThat(service.site(" Test-Co ").openJobs()).isEqualTo(4);
    }

    @Test
    void onlyOpenJobsOfThePageCompany() {
        posting.setApplicationDeadline(TODAY.minusDays(1));
        assertCode(() -> service.job("test-co", 5L), ERecruitmentError.REC_048);
        posting.setApplicationDeadline(null);
        posting.setPublished(false);
        assertCode(() -> service.job("test-co", 5L), ERecruitmentError.REC_048);
        posting.setPublished(true);
        posting.setCompanyId(11L);
        assertCode(() -> service.job("test-co", 5L), ERecruitmentError.REC_048);
    }

    @Test
    void applyRules() {
        assertCode(() -> service.apply("test-co", 5L, form(true, null), null, null), ERecruitmentError.REC_050);
        posting.setShowTerms(true);
        assertCode(() -> service.apply("test-co", 5L, form(false, null), resume, null), ERecruitmentError.REC_049);
        CatalogItem otherCompanySource = new CatalogItem();
        otherCompanySource.setId(8L);
        otherCompanySource.setCompanyId(11L);
        otherCompanySource.setStatus(EStatus.ACTIVE);
        when(items.findByIdAndKindAndDeletedFalse(8L, ECatalogKind.CANDIDATE_SOURCE))
                .thenReturn(Optional.of(otherCompanySource));
        assertCode(() -> service.apply("test-co", 5L, form(true, 8L), resume, null), ERecruitmentError.REC_001);
        posting.setApplicationType(JobPosting.EApplicationType.CUSTOM);
        posting.setApplicationUrl("https://careers.example.invalid/dev");
        assertCode(() -> service.apply("test-co", 5L, form(true, null), resume, null), ERecruitmentError.REC_052);
        verify(candidates, never()).register(any(), any(), any(), any());
    }

    @Test
    void closedPostingRefusesApplications() {
        posting.setStatus(JobPosting.EStatus.CLOSED);
        posting.setPublished(false);
        assertCode(() -> service.apply("test-co", 5L, form(true, null), resume, null), ERecruitmentError.REC_054);
        assertCode(() -> service.job("test-co", 5L), ERecruitmentError.REC_048);
        verify(candidates, never()).register(any(), any(), any(), any());
    }

    @Test
    void trackNeedsTheMatchingEmail() {
        Candidate c = new Candidate();
        c.setCompanyId(10L);
        c.setJobPostingId(5L);
        c.setEmail("applicant@example.invalid");
        c.setTrackingId("ABCDEFGH2345");
        c.setApplicationDate(TODAY);
        c.setStatus(Candidate.EStatus.INTERVIEW);
        c.setNoticePeriod("internal");
        when(candidates.findByTracking("ABCDEFGH2345")).thenReturn(Optional.of(c));
        when(postings.findById(5L)).thenReturn(Optional.of(posting));
        var t = service.track("test-co", " abcdefgh2345 ", "Applicant@Example.invalid");
        assertThat(t).isEqualTo(new PublicCareerService.Tracked("ABCDEFGH2345", "Developer", TODAY,
                Candidate.EStatus.INTERVIEW));
        assertCode(() -> service.track("test-co", "ABCDEFGH2345", "other@example.invalid"), ERecruitmentError.REC_059);
        assertCode(() -> service.track("test-co", "ZZZZZZZZZZZZ", "applicant@example.invalid"),
                ERecruitmentError.REC_059);
        assertCode(() -> service.track("test-co", "", ""), ERecruitmentError.REC_059);
        // another company's page never finds it
        CareerSettings other = new CareerSettings();
        other.setCompanyId(11L);
        other.setSlug("other-co");
        other.setEnabled(true);
        when(settings.findBySlug("other-co")).thenReturn(Optional.of(other));
        assertCode(() -> service.track("other-co", "ABCDEFGH2345", "applicant@example.invalid"),
                ERecruitmentError.REC_059);
    }

    @Test
    void applyDropsFieldsThePostingDoesNotAsk() {
        var applied = service.apply("test-co", 5L, form(true, null), resume, null);
        assertThat(applied.jobTitle()).isEqualTo("Developer");
        assertThat(applied.trackingId()).isEqualTo("ABCDEFGH2345");
        ArgumentCaptor<CandidateForm> captor = ArgumentCaptor.forClass(CandidateForm.class);
        verify(candidates).register(eq(posting), captor.capture(), eq(resume), any());
        assertThat(captor.getValue().gender()).isNull();
        assertThat(captor.getValue().dateOfBirth()).isNull();
        assertThat(captor.getValue().coverLetterMessage()).isNull();
        assertThat(captor.getValue().jobPostingId()).isEqualTo(5L);
    }

    @Test
    void slugRules() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        CareerSettingsService s = new CareerSettingsService(settings);
        assertCode(() -> s.save(null, new SettingsForm("Bad Slug", true, "X", null, null)), ERecruitmentError.REC_051);
        assertCode(() -> s.save(null, new SettingsForm("ab", true, "X", null, null)), ERecruitmentError.REC_051);
        assertCode(() -> s.save(null, new SettingsForm("a--b", true, "X", null, null)), ERecruitmentError.REC_051);
        assertCode(() -> s.save(null, new SettingsForm("off-co", true, "X", null, null)), ERecruitmentError.REC_004);
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(s.save(null, new SettingsForm(" My-Co2 ", true, " My Co ", "", null)))
                .satisfies(d -> {
                    assertThat(d.slug()).isEqualTo("my-co2");
                    assertThat(d.displayName()).isEqualTo("My Co");
                    assertThat(d.headline()).isNull();
                    assertThat(d.publicPath()).isEqualTo("/api/v1/public/career/my-co2");
                });
    }

    private static ApplicationForm form(boolean terms, Long sourceId) {
        return new ApplicationForm("Test", "Applicant", "applicant@example.invalid", null, Candidate.EGender.OTHER,
                LocalDate.of(1990, 1, 1), null, null, null, null, null, null, null, 2, null, null, "Hello", sourceId,
                terms, List.of());
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
