package com.terp.erp.recruitment.posting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.catalog.CatalogItem;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.catalog.ECatalogKind;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.location.JobLocation;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.location.JobLocationService;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingForm;
import com.terp.erp.recruitment.question.CustomQuestionRepository;
import com.terp.erp.recruitment.question.CustomQuestionService;
import com.terp.erp.recruitment.round.InterviewRoundRepository;
import com.terp.erp.recruitment.round.InterviewRoundService;
import com.terp.erp.recruitment.round.InterviewRoundService.RoundForm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobPostingRoundTest {
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final CatalogItemRepository items = mock(CatalogItemRepository.class);
    private final JobLocationRepository locations = mock(JobLocationRepository.class);
    private final CustomQuestionRepository questions = mock(CustomQuestionRepository.class);
    private final InterviewRoundRepository rounds = mock(InterviewRoundRepository.class);
    private final OrgClient org = mock(OrgClient.class);
    private final com.terp.erp.recruitment.candidate.CandidateRepository candidates =
            mock(com.terp.erp.recruitment.candidate.CandidateRepository.class);
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews = mock(com.terp.erp.recruitment.interview.InterviewRepository.class);
    private final CatalogService catalog = new CatalogService(items, postings, candidates, interviews);
    private final JobLocationService locationService = new JobLocationService(locations, postings);
    private final JobPostingService service = new JobPostingService(postings, catalog, items, locationService, locations,
            new CustomQuestionService(questions), questions, org,
            Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC), candidates);
    private final InterviewRoundService roundService = new InterviewRoundService(rounds, postings, service, interviews);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGeneratesTheCodeAndSanitizes() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubTypeAndLocation(EStatus.ACTIVE);
        when(postings.lockSequence(10L, "JOB")).thenReturn(7L);
        when(postings.save(any())).thenAnswer(i -> {
            JobPosting p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
        var d = service.create(null, form(null, null, "<p onclick=\"x()\">Build APIs<script>bad()</script></p>"));
        assertThat(d.jobCode()).isEqualTo("JOB-0007");
        assertThat(d.description()).isEqualTo("<p>Build APIs</p>");
        assertThat(d.status()).isEqualTo(JobPosting.EStatus.DRAFT);
        assertThat(d.skills()).containsExactly("Java", "Spring");
    }

    @Test
    void closeTransitions() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        JobPosting p = new JobPosting();
        p.setId(1L);
        p.setCompanyId(10L);
        p.setJobTypeId(2L);
        p.setLocationId(3L);
        when(postings.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(p));
        assertCode(() -> service.close(1L), ERecruitmentError.REC_056);
        service.setPublished(1L, true);
        assertThat(service.close(1L).status()).isEqualTo(JobPosting.EStatus.CLOSED);
        assertThat(p.isPublished()).isFalse();
        assertCode(() -> service.setPublished(1L, false), ERecruitmentError.REC_055);
        assertCode(() -> service.close(1L), ERecruitmentError.REC_056);
        // re-open
        assertThat(service.setPublished(1L, true).status()).isEqualTo(JobPosting.EStatus.PUBLISHED);
        p.setCompanyId(11L);
        assertCode(() -> service.close(1L), ERecruitmentError.REC_006);
    }

    @Test
    void rules() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubTypeAndLocation(EStatus.ACTIVE);
        PostingForm salary = new PostingForm("Dev", 1L, 2L, null, null, null, null, null, null, null, null, null, false,
                0, null, new BigDecimal("30"), new BigDecimal("10"), null, null, null, null, false, false, false, false);
        assertError(() -> service.create(null, salary), ERecruitmentError.REC_008);
        PostingForm custom = new PostingForm("Dev", 1L, 2L, null, null, null, null, null, null,
                JobPosting.EApplicationType.CUSTOM, " ", null, false, 0, null, null, null, null, null, null, null,
                false, false, false, false);
        assertError(() -> service.create(null, custom), ERecruitmentError.REC_009);
        when(org.department(5L)).thenReturn(new OrgClient.Department("5", "10", "IT", new OrgClient.Ref("3", "HQ"), "ACTIVE"));
        assertError(() -> service.create(null, form(4L, 5L, null)), ERecruitmentError.REC_010);
    }

    @Test
    void inactiveJobTypeCannotBeChosen() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubTypeAndLocation(EStatus.INACTIVE);
        assertError(() -> service.create(null, form(null, null, null)), ERecruitmentError.REC_014);
    }

    @Test
    void publishAndIsolation() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        JobPosting p = posting(10L);
        when(postings.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(p));
        when(items.findById(1L)).thenReturn(Optional.empty());
        when(locations.findById(2L)).thenReturn(Optional.empty());
        var d = service.setPublished(1L, true);
        assertThat(d.status()).isEqualTo(JobPosting.EStatus.PUBLISHED);
        assertThat(d.publishDate()).isNotNull();
        assertThat(service.setPublished(1L, false).status()).isEqualTo(JobPosting.EStatus.DRAFT);
        TestLogin.as(11L, "RECRUITMENT_VIEW");
        assertError(() -> service.get(1L), ERecruitmentError.REC_006);
    }

    @Test
    void roundSequenceIsUniquePerPosting() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        when(postings.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(posting(10L)));
        when(rounds.existsByJobPostingIdAndSequenceNumberAndDeletedFalseAndIdNot(eq(1L), eq(2), anyLong())).thenReturn(true);
        assertError(() -> roundService.create(new RoundForm(1L, "Tech", 2, null, null)), ERecruitmentError.REC_011);
    }

    @Test
    void jobTypeAndLocationInUseCannotBeDeleted() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        stubTypeAndLocation(EStatus.ACTIVE);
        when(postings.existsByJobTypeIdAndDeletedFalse(1L)).thenReturn(true);
        when(postings.existsByLocationIdAndDeletedFalse(2L)).thenReturn(true);
        assertError(() -> catalog.delete(ECatalogKind.JOB_TYPE, 1L), ERecruitmentError.REC_013);
        assertError(() -> locationService.delete(2L), ERecruitmentError.REC_013);
    }

    private void stubTypeAndLocation(EStatus status) {
        CatalogItem type = new CatalogItem();
        type.setId(1L);
        type.setCompanyId(10L);
        type.setKind(ECatalogKind.JOB_TYPE);
        type.setName("Full-time");
        type.setStatus(status);
        when(items.findByIdAndKindAndDeletedFalse(1L, ECatalogKind.JOB_TYPE)).thenReturn(Optional.of(type));
        when(items.findById(1L)).thenReturn(Optional.of(type));
        JobLocation loc = new JobLocation();
        loc.setId(2L);
        loc.setCompanyId(10L);
        loc.setName("HQ");
        when(locations.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(loc));
        when(locations.findById(2L)).thenReturn(Optional.of(loc));
    }

    private static PostingForm form(Long branchId, Long departmentId, String description) {
        return new PostingForm(" Backend Developer ", 1L, 2L, branchId, departmentId, null, List.of(" Java ", "Spring"),
                null, null, null, null, null, false, 2, 5, null, null, description, null, null, null, false, false,
                false, false);
    }

    private static JobPosting posting(Long companyId) {
        JobPosting p = new JobPosting();
        p.setId(1L);
        p.setCompanyId(companyId);
        p.setJobCode("JOB-0001");
        p.setTitle("Dev");
        p.setJobTypeId(1L);
        p.setLocationId(2L);
        return p;
    }

    private static void assertError(Runnable call, ERecruitmentError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
