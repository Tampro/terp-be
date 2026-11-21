package com.terp.erp.recruitment.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.career.CareerSettings;
import com.terp.erp.recruitment.career.CareerSettingsRepository;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.posting.JobPostingRepository;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Company dashboard figures of recruitment (docs/analysis/06 §5.1: Active Jobs card, Career Page card, Candidate
 * Pipeline chart). Recent candidates come from the candidates list.
 * TODO(assumption): an active job = PUBLISHED and its application deadline not passed (or none).
 */
@Service
@RequiredArgsConstructor
public class RecruitmentDashboardService {
    private final JobPostingRepository postings;
    private final CandidateRepository candidates;
    private final CareerSettingsRepository careers;
    private final com.terp.erp.recruitment.interview.InterviewRepository interviews;
    private final com.terp.erp.recruitment.onboarding.CandidateOnboardingRepository onboardings;
    private final Clock clock;

    /** careerSlug: null = no career page or it is switched off. pipeline: every candidate status, zero included. */
    public record RecruitmentDashboard(long activeJobs, long openPositions, String careerSlug,
                                       Map<String, Long> pipeline,
                                       @io.swagger.v3.oas.annotations.media.Schema(description = "Every candidate")
                                       long totalCandidates,
                                       @io.swagger.v3.oas.annotations.media.Schema(description = "SCHEDULED interviews from today on")
                                       long scheduledInterviews,
                                       @io.swagger.v3.oas.annotations.media.Schema(description = "COMPLETED onboardings")
                                       long completedOnboardings,
                                       @io.swagger.v3.oas.annotations.media.Schema(description = "Hiring funnel NEW → HIRED: "
                                               + "candidates that reached each stage and the share of all candidates")
                                       List<FunnelStage> funnel) {
    }

    /** reached = candidates now at this stage or a later one (rejected candidates count only in the total). */
    public record FunnelStage(String stage, long reached, Double percentOfTotal) {
    }

    // TODO(assumption): a REJECTED candidate does not record the stage it left from, so it counts only in the total.
    static final List<Candidate.EStatus> FUNNEL = List.of(Candidate.EStatus.NEW, Candidate.EStatus.SCREENING,
            Candidate.EStatus.INTERVIEW, Candidate.EStatus.OFFER, Candidate.EStatus.HIRED);

    @Transactional(readOnly = true)
    public RecruitmentDashboard dashboard(Long requestedCompanyId) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, ERecruitmentError.REC_005);
        List<Object[]> active = postings.activeCounts(companyId, LocalDate.now(clock));
        Object[] row = active.isEmpty() ? new Object[]{0L, 0L} : active.get(0);
        Map<String, Long> pipeline = new LinkedHashMap<>();
        for (Candidate.EStatus s : Candidate.EStatus.values()) {
            pipeline.put(s.name(), 0L);
        }
        for (Object[] r : candidates.countByStatus(companyId, null, null, "")) {
            pipeline.put(((Candidate.EStatus) r[0]).name(), ((Number) r[1]).longValue());
        }
        String slug = careers.findByCompanyId(companyId).filter(CareerSettings::isEnabled).map(CareerSettings::getSlug)
                .orElse(null);
        long scheduled = interviews.countByStatus(companyId, null, LocalDate.now(clock), null, "").stream()
                .filter(r -> r[0] == com.terp.erp.recruitment.interview.Interview.EStatus.SCHEDULED)
                .mapToLong(r -> num(r[1])).sum();
        long completed = onboardings.countByStatus(companyId, null, "").stream()
                .filter(r -> r[0] == com.terp.erp.recruitment.onboarding.CandidateOnboarding.EStatus.COMPLETED)
                .mapToLong(r -> num(r[1])).sum();
        long total = pipeline.values().stream().mapToLong(Long::longValue).sum();
        return new RecruitmentDashboard(num(row[0]), num(row[1]), slug, pipeline, total, scheduled, completed,
                funnel(pipeline, total));
    }

    static List<FunnelStage> funnel(Map<String, Long> pipeline, long total) {
        List<FunnelStage> out = new java.util.ArrayList<>();
        for (int i = 0; i < FUNNEL.size(); i++) {
            long reached = 0;
            for (int j = i; j < FUNNEL.size(); j++) {
                reached += pipeline.getOrDefault(FUNNEL.get(j).name(), 0L);
            }
            out.add(new FunnelStage(FUNNEL.get(i).name(), reached,
                    total == 0 ? null : Math.round(reached * 1000.0 / total) / 10.0));
        }
        return out;
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
