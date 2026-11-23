package com.terp.erp.recruitment.offer;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.career.CareerSettingsRepository;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.client.RemoteApiException;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.offer.OfferConversionService.ConvertForm;
import com.terp.erp.recruitment.offer.OfferConversionService.EEmploymentType;
import com.terp.erp.recruitment.offertemplate.OfferTemplate;
import com.terp.erp.recruitment.offertemplate.OfferTemplateRepository;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OfferConversionServiceTest {
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CandidateService candidateService = mock(CandidateService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final OrgClient org = mock(OrgClient.class);
    private final OfferTemplateRepository templateRepo = mock(OfferTemplateRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ZoneOffset.UTC);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final OfferService offerService = new OfferService(offers, candidateService, candidates,
            mock(JobPostingService.class), postings, mock(CatalogItemRepository.class), org, employees, clock);
    private final OfferConversionService service = new OfferConversionService(offerService, candidateService,
            employees, clock, outbox);
    private final OfferLetterService letters = new OfferLetterService(offerService,
            new OfferTemplateService(templateRepo), candidates, postings, mock(CareerSettingsRepository.class), org, outbox);

    private Candidate candidate;
    private Offer offer;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCompanyId(10L);
        candidate.setFirstName("Test");
        candidate.setLastName("Candidate");
        candidate.setEmail("test.candidate@example.invalid");
        candidate.setStatus(Candidate.EStatus.OFFER);
        when(candidateService.require(7L)).thenReturn(candidate);
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate));
        offer = new Offer();
        offer.setId(9L);
        offer.setCompanyId(10L);
        offer.setCandidateId(7L);
        offer.setJobPostingId(1L);
        offer.setDepartmentId(3L);
        offer.setSalary(new BigDecimal("25000000"));
        offer.setStartDate(LocalDate.of(2027, 1, 4));
        offer.setStatus(Offer.EStatus.ACCEPTED);
        when(offers.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(offer));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void convertCreatesTheEmployeeAndHires() {
        when(employees.create(eq(10L), any())).thenReturn(new EmployeeClient.Employee("55", "10",
                "Test Candidate", "test.candidate@example.invalid", null));
        var r = service.convert(9L, form());
        assertThat(r.employeeId()).isEqualTo("55");
        assertThat(candidate.getEmployeeId()).isEqualTo(55L);
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.HIRED);
        var sent = (Map<String, Object>) service.employeeForm(offer, candidate, form());
        assertThat(sent).containsEntry("email", "test.candidate@example.invalid");
        var profile = (Map<String, Object>) sent.get("profile");
        assertThat(profile).containsEntry("fullName", "Test Candidate").containsEntry("dateOfJoining", "2027-01-04")
                .containsEntry("departmentId", 3L).containsEntry("employmentType", "FULL_TIME")
                .containsEntry("status", "PROBATION");
        var event = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("recruitment.candidate.converted"), eq("CANDIDATE"), eq(7L), event.capture());
        assertThat((Map<String, Object>) event.getValue())
                .containsKeys("candidateId", "title", "name", "offerId", "employeeId", "jobPostingId")
                .containsEntry("employeeId", "55");
        // once only
        assertCode(() -> service.convert(9L, form()), ERecruitmentError.REC_061);
    }

    @Test
    void onlyAnAcceptedOffer() {
        offer.setStatus(Offer.EStatus.SENT);
        assertCode(() -> service.convert(9L, form()), ERecruitmentError.REC_060);
        verify(employees, never()).create(any(), any());
    }

    @Test
    void nothingSavedWhenEmployeeServiceRefuses() {
        when(employees.create(eq(10L), any())).thenThrow(new RemoteApiException(HttpStatus.BAD_REQUEST, "EMP_007",
                "Email used"));
        assertThatThrownBy(() -> service.convert(9L, form())).isInstanceOf(RemoteApiException.class);
        assertThat(candidate.getEmployeeId()).isNull();
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.OFFER);
    }

    @Test
    void otherCompanyOfferIsNotFound() {
        offer.setCompanyId(11L);
        assertCode(() -> service.convert(9L, form()), ERecruitmentError.REC_035);
        assertCode(() -> letters.letter(9L, 4L), ERecruitmentError.REC_035);
        verify(employees, never()).create(any(), any());
    }

    @Test
    void letterFillsTheTemplateAsPdf() {
        JobPosting p = new JobPosting();
        p.setId(1L);
        p.setTitle("Developer");
        p.setJobCode("JOB-0001");
        when(postings.findById(1L)).thenReturn(Optional.of(p));
        when(org.departmentName(3L)).thenReturn("IT");
        OfferTemplate t = new OfferTemplate();
        t.setId(4L);
        t.setCompanyId(10L);
        t.setName("Standard offer");
        t.setContent("<p>Dear {{candidate_name}},</p><p>Position {{position}}, salary {{salary}}, from "
                + "{{start_date}} {{unknown_var}}</p>");
        t.setVariables(new java.util.ArrayList<>(List.of("candidate_name", "position", "salary", "start_date",
                "unknown_var")));
        when(templateRepo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(t));
        var l = letters.letter(9L, 4L);
        assertThat(new String(l.pdf(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(letters.values(offer)).containsEntry("candidate_name", "Test Candidate")
                .containsEntry("salary", "25,000,000").containsEntry("department", "IT");
        assertThat(OfferPdf.paragraphs("<p>Dear A &amp; B,</p><p>line<br>two</p>"))
                .containsExactly("Dear A & B,", "line", "two");
        // another company's template
        t.setCompanyId(11L);
        assertCode(() -> letters.letter(9L, 4L), ERecruitmentError.REC_032);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendLetterPublishesThePlainTextToTheCandidate() {
        OfferTemplate t = template(10L);
        when(templateRepo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(t));
        when(outbox.publish(any(), any(), any(), any(), any())).thenReturn("evt-1");
        for (Offer.EStatus ok : List.of(Offer.EStatus.SENT, Offer.EStatus.NEGOTIATING, Offer.EStatus.ACCEPTED)) {
            offer.setStatus(ok);
            assertThat(letters.send(9L, 4L)).isEqualTo(new OfferLetterService.LetterSent("9", "evt-1"));
        }
        var event = ArgumentCaptor.forClass(Object.class);
        verify(outbox, times(3)).publish(eq(10L), eq("recruitment.offer.letter_sent"), eq("OFFER"),
                eq(9L), event.capture());
        Map<String, Object> m = (Map<String, Object>) event.getValue();
        assertThat(m).containsKeys("offerId", "title", "candidateId", "candidateName", "jobTitle", "templateId")
                .containsEntry("recipientEmail", "test.candidate@example.invalid")
                .containsEntry("letterText", "Dear Test Candidate,\n\nWelcome aboard.");
    }

    @Test
    void sendLetterRules() {
        when(templateRepo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(template(10L)));
        for (Offer.EStatus bad : List.of(Offer.EStatus.DRAFT, Offer.EStatus.DECLINED, Offer.EStatus.EXPIRED)) {
            offer.setStatus(bad);
            assertCode(() -> letters.send(9L, 4L), ERecruitmentError.REC_068);
        }
        offer.setStatus(Offer.EStatus.SENT);
        when(templateRepo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(template(11L)));
        assertCode(() -> letters.send(9L, 4L), ERecruitmentError.REC_032);
        offer.setCompanyId(11L);
        assertCode(() -> letters.send(9L, 4L), ERecruitmentError.REC_035);
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
        // the 20 KB cap never splits a multi-byte character
        String cut = OfferLetterService.truncate("\u1EA0".repeat(10_000), OfferLetterService.MAX_LETTER_BYTES);
        assertThat(cut.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(20 * 1024).isGreaterThan(20 * 1024 - 3);
    }

    private static OfferTemplate template(Long companyId) {
        OfferTemplate t = new OfferTemplate();
        t.setId(4L);
        t.setCompanyId(companyId);
        t.setName("Standard offer");
        t.setContent("<p>Dear {{candidate_name}},</p><p>Welcome aboard.</p>");
        t.setVariables(new java.util.ArrayList<>(List.of("candidate_name")));
        return t;
    }

    private static ConvertForm form() {
        return new ConvertForm(1L, null, 4L, null, EEmploymentType.FULL_TIME, null, null, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
