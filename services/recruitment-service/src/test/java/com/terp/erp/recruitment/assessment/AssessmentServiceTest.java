package com.terp.erp.recruitment.assessment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.assessment.AssessmentService.AssessmentForm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssessmentServiceTest {
    private final CandidateAssessmentRepository assessments = mock(CandidateAssessmentRepository.class);
    private final CandidateService candidateService = mock(CandidateService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final com.terp.erp.recruitment.interview.InterviewFeedbackRepository feedbacks =
            mock(com.terp.erp.recruitment.interview.InterviewFeedbackRepository.class);
    private final AssessmentService service = new AssessmentService(assessments, candidateService, candidates,
            employees, feedbacks);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createComputesThePercentage() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        Candidate c = new Candidate();
        c.setId(7L);
        c.setCompanyId(10L);
        c.setFirstName("Test");
        c.setLastName("Candidate");
        when(candidateService.require(7L)).thenReturn(c);
        when(candidates.findById(7L)).thenReturn(Optional.of(c));
        when(feedbacks.candidateRecommended(7L, AssessmentService.RECOMMENDED)).thenReturn(true);
        when(employees.employee(5L)).thenReturn(new EmployeeClient.Employee("5", "10", "Test Assessor", null, null));
        when(assessments.save(any())).thenAnswer(i -> {
            CandidateAssessment a = i.getArgument(0);
            a.setId(1L);
            return a;
        });
        var d = service.create(form("42", "50"));
        assertThat(d.percentage()).isEqualByComparingTo("84.0");
        assertThat(d.status()).isEqualTo(CandidateAssessment.EStatus.PENDING);
        assertThat(d.conductedBy().name()).isEqualTo("Test Assessor");
        assertThat(d.candidate().name()).isEqualTo("Test Candidate");
        assertCode(() -> service.create(form("51", "50")), ERecruitmentError.REC_046);
    }

    @Test
    void onlyForCandidatesRecommendedHire() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        Candidate c = new Candidate();
        c.setId(7L);
        c.setCompanyId(10L);
        when(candidateService.require(7L)).thenReturn(c);
        when(feedbacks.candidateRecommended(7L, AssessmentService.RECOMMENDED)).thenReturn(false);
        assertCode(() -> service.create(form("42", "50")), ERecruitmentError.REC_057);
        org.mockito.Mockito.verify(assessments, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(employees, org.mockito.Mockito.never()).employee(any());
        assertThat(AssessmentService.RECOMMENDED).containsExactlyInAnyOrder(
                com.terp.erp.recruitment.interview.InterviewFeedback.ERecommendation.HIRE,
                com.terp.erp.recruitment.interview.InterviewFeedback.ERecommendation.STRONG_HIRE);
    }

    @Test
    void percentageRoundsToOneDecimal() {
        assertThat(AssessmentService.percentage(new BigDecimal("2"), new BigDecimal("3"))).isEqualByComparingTo("66.7");
    }

    @Test
    void otherCompanyIsNotFound() {
        TestLogin.as(11L, "RECRUITMENT_VIEW");
        CandidateAssessment a = new CandidateAssessment();
        a.setId(1L);
        a.setCompanyId(10L);
        when(assessments.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(a));
        assertCode(() -> service.get(1L), ERecruitmentError.REC_041);
    }

    private static AssessmentForm form(String score, String max) {
        return new AssessmentForm(7L, "Technical Skills Assessment", new BigDecimal(score), new BigDecimal(max), null,
                5L, LocalDate.of(2026, 11, 20), null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
