package com.terp.erp.crm.record;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskPriority;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.crm.setup.StageRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRM dashboard (ERP manual §5.6): counts, upcoming lead tasks, deals by stage, recent deals / leads. Own scope
 * (CRM_VIEW_OWN, or own=true for the staff dashboard) uses the same "mine" rule as the lead / deal lists.
 */
@Service
@RequiredArgsConstructor
public class CrmDashboardService {
    static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";
    static final int RECENT = 5;

    private final LeadRepository leads;
    private final DealRepository deals;
    private final CrmLinkRepository links;
    private final CrmTaskRepository tasks;
    private final CrmCallRepository calls;
    private final StageRepository stages;
    private final Clock clock;

    public record UpcomingTask(String id, String leadId, String leadName, String name, LocalDate taskDate,
                               LocalTime taskTime, ETaskPriority priority) {
    }

    public record StageCount(String stageId, String stageName, String pipelineId, long count, BigDecimal value) {
    }

    public record RecentDeal(String id, String name, BigDecimal price, EDealStatus status, String stageId,
                             Instant createdDate) {
    }

    public record RecentLead(String id, String name, String subject, String ownerUserId, String stageId,
                             Instant createdDate) {
    }

    @Schema(description = "CRM dashboard; scope OWN = only my leads / deals")
    public record CrmDashboard(String scope, long deals, long leads,
                               @Schema(description = "Distinct lead owners and deal members") long users,
                               @Schema(description = "Distinct CLIENT customers of the deals") long clients,
                               long calls, List<UpcomingTask> upcomingTasks, List<StageCount> dealsByStage,
                               List<RecentDeal> recentDeals, List<RecentLead> recentLeads) {
    }

    @Transactional(readOnly = true)
    public CrmDashboard dashboard(Long companyId, String tz, boolean ownRequested) {
        ZoneId zone = zone(tz);
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        boolean own = ownRequested || CrmScope.ownView();
        List<Lead> ls = own ? leads.listOwn(company, OwnScope.userId(), OwnScope.sub())
                : leads.findByCompanyIdAndDeletedFalse(company);
        List<Deal> ds = own
                ? deals.listOwn(company, OwnScope.userId(), OwnScope.sub(), ESubject.DEAL, ELinkType.MEMBER)
                : deals.findByCompanyIdAndDeletedFalse(company);
        List<Long> leadIds = ls.stream().map(Lead::getId).toList();
        List<Long> dealIds = ds.stream().map(Deal::getId).toList();

        Set<Long> users = new LinkedHashSet<>();
        ls.forEach(l -> users.add(l.getOwnerUserId()));
        long clients = 0;
        long callCount = 0;
        if (!dealIds.isEmpty()) {
            links.findBySubjectTypeAndSubjectIdInAndLinkType(ESubject.DEAL, dealIds, ELinkType.MEMBER)
                    .forEach(k -> users.add(k.getRefId()));
            clients = links.findBySubjectTypeAndSubjectIdInAndLinkType(ESubject.DEAL, dealIds, ELinkType.CLIENT)
                    .stream().map(CrmLink::getRefId).distinct().count();
            callCount += calls.countBySubjectTypeAndSubjectIdInAndDeletedFalse(ESubject.DEAL, dealIds);
        }
        users.remove(null);

        List<UpcomingTask> upcoming = List.of();
        if (!leadIds.isEmpty()) {
            callCount += calls.countBySubjectTypeAndSubjectIdInAndDeletedFalse(ESubject.LEAD, leadIds);
            Map<Long, Lead> byId = ls.stream().collect(Collectors.toMap(Lead::getId, Function.identity()));
            LocalDate today = LocalDate.now(clock.withZone(zone));
            upcoming = tasks
                    .findTop10BySubjectTypeAndSubjectIdInAndStatusAndTaskDateGreaterThanEqualAndDeletedFalseOrderByTaskDateAscTaskTimeAscIdAsc(
                            ESubject.LEAD, leadIds, ETaskStatus.OPEN, today).stream()
                    .map(t -> new UpcomingTask(t.getId().toString(), t.getSubjectId().toString(),
                            byId.containsKey(t.getSubjectId()) ? byId.get(t.getSubjectId()).getName() : null,
                            t.getName(), t.getTaskDate(), t.getTaskTime(), t.getPriority()))
                    .toList();
        }

        Map<Long, List<Deal>> perStage = ds.stream().collect(Collectors.groupingBy(Deal::getStageId,
                LinkedHashMap::new, Collectors.toList()));
        List<StageCount> byStage = new ArrayList<>();
        if (!perStage.isEmpty()) {
            List<Stage> st = new ArrayList<>(stages.findByIdIn(perStage.keySet()));
            st.sort(Comparator.comparing(Stage::getPipelineId).thenComparingInt(Stage::getPosition)
                    .thenComparing(Stage::getId));
            for (Stage s : st) {
                List<Deal> in = perStage.get(s.getId());
                byStage.add(new StageCount(s.getId().toString(), s.getName(), s.getPipelineId().toString(),
                        in.size(), in.stream().map(Deal::getPrice).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
            }
        }

        Comparator<Instant> newest = Comparator.nullsLast(Comparator.reverseOrder());
        List<RecentDeal> recentDeals = ds.stream()
                .sorted(Comparator.comparing(Deal::getCreatedDate, newest)
                        .thenComparing(Deal::getId, Comparator.reverseOrder()))
                .limit(RECENT)
                .map(d -> new RecentDeal(d.getId().toString(), d.getName(), d.getPrice(), d.getStatus(),
                        d.getStageId().toString(), d.getCreatedDate()))
                .toList();
        List<RecentLead> recentLeads = ls.stream()
                .sorted(Comparator.comparing(Lead::getCreatedDate, newest)
                        .thenComparing(Lead::getId, Comparator.reverseOrder()))
                .limit(RECENT)
                .map(l -> new RecentLead(l.getId().toString(), l.getName(), l.getSubject(),
                        l.getOwnerUserId() == null ? null : l.getOwnerUserId().toString(),
                        l.getStageId().toString(), l.getCreatedDate()))
                .toList();
        return new CrmDashboard(own ? "OWN" : "ALL", ds.size(), ls.size(), users.size(), clients, callCount,
                upcoming, byStage, recentDeals, recentLeads);
    }

    // TODO(assumption): the company time zone is passed by the caller (report-service reads it from IAM settings);
    // Asia/Ho_Chi_Minh when none is given.
    static ZoneId zone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? DEFAULT_ZONE : tz.trim());
        } catch (DateTimeException e) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
    }
}
