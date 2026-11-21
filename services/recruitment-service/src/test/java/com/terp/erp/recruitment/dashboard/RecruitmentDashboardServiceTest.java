package com.terp.erp.recruitment.dashboard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.career.CareerSettings;
import com.terp.erp.recruitment.career.CareerSettingsRepository;
import com.terp.erp.recruitment.posting.JobPostingRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecruitmentDashboardServiceTest {
    private final JobPostingRepository postings = mock(JobPostingRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CareerSettingsRepository careers = mock(CareerSettingsRepository.class);
    private final RecruitmentDashboardService service = new RecruitmentDashboardService(postings, candidates, careers,
            Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsAndPipelineWithEveryStatus() {
        TestLogin.as(10L, "RECRUITMENT_VIEW");
        when(postings.activeCounts(10L, LocalDate.of(2026, 11, 21))).thenReturn(List.<Object[]>of(new Object[]{3L, 7L}));
        when(candidates.countByStatus(10L, null, null, "")).thenReturn(List.<Object[]>of(
                new Object[]{Candidate.EStatus.NEW, 4L}, new Object[]{Candidate.EStatus.OFFER, 1L}));
        CareerSettings off = new CareerSettings();
        off.setSlug("acme");
        off.setEnabled(false);
        when(careers.findByCompanyId(10L)).thenReturn(Optional.of(off));

        var d = service.dashboard(null);

        assertThat(d.activeJobs()).isEqualTo(3);
        assertThat(d.openPositions()).isEqualTo(7);
        assertThat(d.careerSlug()).isNull();
        assertThat(d.pipeline()).containsEntry("NEW", 4L).containsEntry("OFFER", 1L).containsEntry("HIRED", 0L)
                .hasSize(Candidate.EStatus.values().length);
        off.setEnabled(true);
        assertThat(service.dashboard(null).careerSlug()).isEqualTo("acme");
    }
}
