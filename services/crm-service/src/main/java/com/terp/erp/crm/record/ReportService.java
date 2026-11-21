package com.terp.erp.crm.record;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.Pipeline;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.crm.setup.Source;
import com.terp.erp.crm.setup.SourceRepository;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lead and deal reports (ERP manual 7.4): per stage (one pipeline), per source, per month of a year, per user, and
 * deals per status. Months are UTC.
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final LeadRepository leads;
    private final DealRepository deals;
    private final CrmLinkRepository links;
    private final SourceRepository sources;
    private final SetupService setup;

    public record Count(String id, String name, long count) {
    }

    public record Month(int month, long created, long converted) {
    }

    public record LeadReport(int year, long total, long converted, Double conversionPercent, List<Count> byStage,
                             List<Count> bySource, List<Month> byMonth, List<Count> byUser) {
    }

    public record Amount(String id, String name, long count, BigDecimal value) {
    }

    public record DealMonth(int month, long count, BigDecimal value) {
    }

    public record DealReport(int year, long total, BigDecimal totalValue, Map<EDealStatus, Amount> byStatus,
                             List<Amount> byStage, List<Count> bySource, List<DealMonth> byMonth) {
    }

    @Transactional(readOnly = true)
    public LeadReport leads(Long companyId, Long pipelineId, Integer year) {
        Long company = scope(companyId, pipelineId);
        int y = year(year);
        Instant from = start(y), to = start(y + 1);
        List<Month> months = new ArrayList<>();
        Map<Integer, long[]> m = new HashMap<>();
        for (Object[] r : leads.countByMonth(company, pipelineId, from, to)) {
            m.put(((Number) r[0]).intValue(), new long[]{num(r[1]), num(r[2])});
        }
        long total = 0, converted = 0;
        for (int i = 1; i <= 12; i++) {
            long[] v = m.getOrDefault(i, new long[]{0, 0});
            months.add(new Month(i, v[0], v[1]));
            total += v[0];
            converted += v[1];
        }
        List<Count> byStage = new ArrayList<>();
        if (pipelineId != null) {
            Map<Long, Long> counts = new HashMap<>();
            leads.countByStage(company, pipelineId).forEach(r -> counts.put((Long) r[0], num(r[1])));
            for (Stage s : setup.stagesOf(pipelineId, EStageKind.LEAD)) {
                byStage.add(new Count(s.getId().toString(), s.getName(), counts.getOrDefault(s.getId(), 0L)));
            }
        }
        List<Count> byUser = leads.countByOwner(company, pipelineId, from, to).stream()
                .map(r -> new Count(r[0].toString(), null, num(r[1]))).toList();
        // TODO(assumption): conversion rate = leads converted / leads created in the year.
        Double rate = total == 0 ? null : Math.round(converted * 1000.0 / total) / 10.0;
        return new LeadReport(y, total, converted, rate, byStage, bySource(company, ESubject.LEAD), months, byUser);
    }

    @Transactional(readOnly = true)
    public DealReport deals(Long companyId, Long pipelineId, Integer year) {
        Long company = scope(companyId, pipelineId);
        int y = year(year);
        Instant from = start(y), to = start(y + 1);
        Map<EDealStatus, Amount> byStatus = new EnumMap<>(EDealStatus.class);
        for (EDealStatus s : EDealStatus.values()) {
            byStatus.put(s, new Amount(s.name(), s.name(), 0, BigDecimal.ZERO));
        }
        long total = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (Object[] r : deals.countByStatus(company, pipelineId, from, to)) {
            EDealStatus s = (EDealStatus) r[0];
            byStatus.put(s, new Amount(s.name(), s.name(), num(r[1]), (BigDecimal) r[2]));
            total += num(r[1]);
            value = value.add((BigDecimal) r[2]);
        }
        Map<Integer, Object[]> m = new HashMap<>();
        deals.countByMonth(company, pipelineId, from, to).forEach(r -> m.put(((Number) r[0]).intValue(), r));
        List<DealMonth> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            Object[] r = m.get(i);
            months.add(new DealMonth(i, r == null ? 0 : num(r[1]), r == null ? BigDecimal.ZERO : (BigDecimal) r[2]));
        }
        List<Amount> byStage = new ArrayList<>();
        if (pipelineId != null) {
            Map<Long, Object[]> counts = new HashMap<>();
            deals.countByStage(company, pipelineId).forEach(r -> counts.put((Long) r[0], r));
            for (Stage s : setup.stagesOf(pipelineId, EStageKind.DEAL)) {
                Object[] r = counts.get(s.getId());
                byStage.add(new Amount(s.getId().toString(), s.getName(), r == null ? 0 : num(r[1]),
                        r == null ? BigDecimal.ZERO : (BigDecimal) r[2]));
            }
        }
        return new DealReport(y, total, value, byStatus, byStage, bySource(company, ESubject.DEAL), months);
    }

    private List<Count> bySource(Long company, ESubject type) {
        Map<Long, Long> counts = new HashMap<>();
        links.countByRef(company, type, ELinkType.SOURCE).forEach(r -> counts.put((Long) r[0], num(r[1])));
        List<Count> out = new ArrayList<>();
        for (Source s : sources.findByCompanyIdAndDeletedFalseOrderByNameAsc(company)) {
            out.add(new Count(s.getId().toString(), s.getName(), counts.getOrDefault(s.getId(), 0L)));
        }
        return out;
    }

    /** With a pipeline: its company (404 for another company's); without: the caller's company. */
    private Long scope(Long companyId, Long pipelineId) {
        if (pipelineId != null) {
            Pipeline p = setup.requirePipeline(pipelineId);
            return p.getCompanyId();
        }
        return TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
    }

    private static int year(Integer year) {
        return year == null ? LocalDate.now(ZoneOffset.UTC).getYear() : year;
    }

    private static Instant start(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
