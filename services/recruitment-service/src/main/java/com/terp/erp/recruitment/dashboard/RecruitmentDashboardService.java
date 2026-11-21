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
    private final Clock clock;

    /** careerSlug: null = no career page or it is switched off. pipeline: every candidate status, zero included. */
    public record RecruitmentDashboard(long activeJobs, long openPositions, String careerSlug,
                                       Map<String, Long> pipeline) {
    }

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
        return new RecruitmentDashboard(num(row[0]), num(row[1]), slug, pipeline);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
