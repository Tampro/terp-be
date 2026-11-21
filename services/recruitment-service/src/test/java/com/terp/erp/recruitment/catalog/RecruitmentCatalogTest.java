package com.terp.erp.recruitment.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.catalog.CatalogDtos.CatalogForm;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.location.JobLocation;
import com.terp.erp.recruitment.location.JobLocationRepository;
import com.terp.erp.recruitment.location.JobLocationService;
import com.terp.erp.recruitment.location.JobLocationService.LocationForm;
import com.terp.erp.recruitment.question.CustomQuestion;
import com.terp.erp.recruitment.question.CustomQuestionRepository;
import com.terp.erp.recruitment.question.CustomQuestionService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecruitmentCatalogTest {
    private final CatalogItemRepository items = mock(CatalogItemRepository.class);
    private final JobLocationRepository locations = mock(JobLocationRepository.class);
    private final CustomQuestionRepository questions = mock(CustomQuestionRepository.class);
    private final com.terp.erp.recruitment.posting.JobPostingRepository postings =
            mock(com.terp.erp.recruitment.posting.JobPostingRepository.class);
    private final com.terp.erp.recruitment.candidate.CandidateRepository candidates =
            mock(com.terp.erp.recruitment.candidate.CandidateRepository.class);
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews = mock(com.terp.erp.recruitment.interview.InterviewRepository.class);
    private final CatalogService catalog = new CatalogService(items, postings, candidates, interviews);
    private final JobLocationService locationService = new JobLocationService(locations, postings);
    private final CustomQuestionService questionService = new CustomQuestionService(questions);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createKeepsTheKindAndTrims() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        when(items.save(any())).thenAnswer(i -> {
            CatalogItem c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
        var d = catalog.create(ECatalogKind.JOB_TYPE, null, new CatalogForm(" Full-time ", " ", null));
        assertThat(d.kind()).isEqualTo(ECatalogKind.JOB_TYPE);
        assertThat(d.name()).isEqualTo("Full-time");
        assertThat(d.description()).isNull();
        assertThat(d.status()).isEqualTo(EStatus.ACTIVE);
    }

    @Test
    void nameIsUniquePerCompanyAndKind() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        when(items.nameTaken(10L, ECatalogKind.JOB_CATEGORY, "Engineering", -1L)).thenReturn(true);
        assertError(() -> catalog.create(ECatalogKind.JOB_CATEGORY, null, new CatalogForm("Engineering", null, null)),
                ERecruitmentError.REC_004);
    }

    @Test
    void anItemOfAnotherKindOrCompanyIsNotFound() {
        TestLogin.as(10L, "RECRUITMENT_VIEW");
        when(items.findByIdAndKindAndDeletedFalse(1L, ECatalogKind.JOB_TYPE)).thenReturn(Optional.empty());
        assertError(() -> catalog.get(ECatalogKind.JOB_TYPE, 1L), ERecruitmentError.REC_001);
        CatalogItem c = new CatalogItem();
        c.setId(2L);
        c.setCompanyId(11L);
        c.setKind(ECatalogKind.JOB_CATEGORY);
        when(items.findByIdAndKindAndDeletedFalse(2L, ECatalogKind.JOB_CATEGORY)).thenReturn(Optional.of(c));
        assertError(() -> catalog.get(ECatalogKind.JOB_CATEGORY, 2L), ERecruitmentError.REC_001);
        assertThat(ECatalogKind.ofPath("candidate-sources")).isEqualTo(ECatalogKind.CANDIDATE_SOURCE);
    }

    @Test
    void locationFullReplacementAndIsolation() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        JobLocation l = new JobLocation();
        l.setId(3L);
        l.setCompanyId(10L);
        l.setCity("Ha Noi");
        when(locations.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(l));
        var d = locationService.update(3L, new LocationForm("Remote", true, null, null, null, null, null, null));
        assertThat(d.remote()).isTrue();
        assertThat(d.city()).isNull();
        TestLogin.as(11L, "RECRUITMENT_MANAGE");
        assertError(() -> locationService.get(3L), ERecruitmentError.REC_002);
    }

    @Test
    void questionOfAnotherCompanyIsNotFound() {
        TestLogin.as(11L, "RECRUITMENT_VIEW");
        CustomQuestion q = new CustomQuestion();
        q.setId(4L);
        q.setCompanyId(10L);
        when(questions.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(q));
        assertError(() -> questionService.get(4L), ERecruitmentError.REC_003);
    }

    @Test
    void superAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertError(() -> catalog.options(ECatalogKind.INTERVIEW_TYPE, null), ERecruitmentError.REC_005);
    }

    private static void assertError(Runnable call, ERecruitmentError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
