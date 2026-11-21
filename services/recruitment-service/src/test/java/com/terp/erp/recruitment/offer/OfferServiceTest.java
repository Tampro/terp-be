package com.terp.erp.recruitment.offer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.catalog.CatalogItemRepository;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.client.OrgClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.offer.OfferService.OfferForm;
import com.terp.erp.recruitment.posting.JobPosting;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.recruitment.posting.JobPostingService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferServiceTest {
    private final OfferRepository offers = mock(OfferRepository.class);
    private final CandidateService candidateService = mock(CandidateService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final JobPostingService postingService = mock(JobPostingService.class);
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final OrgClient org = mock(OrgClient.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    /** 2026-11-19 17:30 UTC = 2026-11-20 in Vietnam. */
    private final OfferService service = new OfferService(offers, candidateService, candidates, postingService,
            postings, mock(CatalogItemRepository.class), org, employees,
            Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ZoneOffset.UTC));

    private Candidate candidate;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCompanyId(10L);
        candidate.setJobPostingId(1L);
        candidate.setStatus(Candidate.EStatus.INTERVIEW);
        when(candidateService.require(7L)).thenReturn(candidate);
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate));
        when(candidates.findAllById(any())).thenReturn(List.of(candidate));
        posting = new JobPosting();
        posting.setId(1L);
        posting.setCompanyId(10L);
        posting.setJobTypeId(2L);
        posting.setDepartmentId(3L);
        posting.setTitle("Developer");
        when(postingService.require(1L)).thenReturn(posting);
        when(postings.findAllById(any())).thenReturn(List.of(posting));
        when(org.departmentName(3L)).thenReturn("IT");
        when(employees.employee(5L)).thenReturn(new EmployeeClient.Employee("5", "10", "Test Approver", null, null));
        when(offers.save(any())).thenAnswer(i -> {
            Offer o = i.getArgument(0);
            o.setId(9L);
            return o;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDefaultsAndMovesTheCandidate() {
        var d = service.create(form(null, LocalDate.of(2027, 1, 4), LocalDate.of(2026, 12, 15), 5L));
        assertThat(d.status()).isEqualTo(Offer.EStatus.DRAFT);
        assertThat(d.offerDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(d.job().title()).isEqualTo("Developer");
        assertThat(d.department().name()).isEqualTo("IT");
        assertThat(d.approvedBy().name()).isEqualTo("Test Approver");
        assertThat(d.expired()).isFalse();
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.OFFER);
        verify(org).department(3L);
    }

    @Test
    void rules() {
        assertCode(() -> service.create(form(null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2), null)),
                ERecruitmentError.REC_037);
        candidate.setStatus(Candidate.EStatus.HIRED);
        assertCode(() -> service.create(form(null, null, null, null)), ERecruitmentError.REC_036);
        verify(offers, never()).save(any());
    }

    @Test
    void acceptedHiresTheCandidate() {
        Offer o = offer(Offer.EStatus.SENT, null);
        when(offers.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(o));
        service.changeStatus(9L, Offer.EStatus.NEGOTIATING);
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.INTERVIEW);
        service.changeStatus(9L, Offer.EStatus.ACCEPTED);
        assertThat(candidate.getStatus()).isEqualTo(Candidate.EStatus.HIRED);
    }

    @Test
    void expiredFlagOnlyForOpenOffers() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        assertThat(OfferService.expired(offer(Offer.EStatus.SENT, today.minusDays(1)), today)).isTrue();
        assertThat(OfferService.expired(offer(Offer.EStatus.SENT, today), today)).isFalse();
        assertThat(OfferService.expired(offer(Offer.EStatus.ACCEPTED, today.minusDays(1)), today)).isFalse();
        assertThat(OfferService.expired(offer(Offer.EStatus.DRAFT, null), today)).isFalse();
    }

    @Test
    void otherCompanyOfferIsNotFound() {
        Offer o = offer(Offer.EStatus.DRAFT, null);
        o.setCompanyId(11L);
        when(offers.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(o));
        assertCode(() -> service.get(9L), ERecruitmentError.REC_035);
    }

    private static Offer offer(Offer.EStatus status, LocalDate expiration) {
        Offer o = new Offer();
        o.setId(9L);
        o.setCompanyId(10L);
        o.setCandidateId(7L);
        o.setJobPostingId(1L);
        o.setSalary(BigDecimal.TEN);
        o.setStatus(status);
        o.setExpirationDate(expiration);
        return o;
    }

    private static OfferForm form(Long postingId, LocalDate start, LocalDate expiration, Long approver) {
        return new OfferForm(7L, postingId, null, new BigDecimal("25000000"), start, expiration, approver, " Laptop ");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
