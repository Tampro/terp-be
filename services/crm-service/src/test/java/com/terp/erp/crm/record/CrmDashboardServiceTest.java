package com.terp.erp.crm.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.crm.setup.StageRepository;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmDashboardServiceTest {
    // 2026-11-22 18:00 UTC = 2026-11-23 01:00 in Ho Chi Minh City
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T18:00:00Z"), ZoneOffset.UTC);
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final CrmLinkRepository links = mock(CrmLinkRepository.class);
    private final CrmTaskRepository tasks = mock(CrmTaskRepository.class);
    private final CrmCallRepository calls = mock(CrmCallRepository.class);
    private final StageRepository stages = mock(StageRepository.class);
    private final CrmDashboardService service = new CrmDashboardService(leads, deals, links, tasks, calls, stages,
            clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Lead lead(long id, long owner, String created) {
        Lead l = new Lead();
        l.setId(id);
        l.setCompanyId(10L);
        l.setName("Lead " + id);
        l.setSubject("S" + id);
        l.setOwnerUserId(owner);
        l.setStageId(1L);
        l.setCreatedDate(Instant.parse(created));
        return l;
    }

    private static Deal deal(long id, long stage, String price, String created) {
        Deal d = new Deal();
        d.setId(id);
        d.setCompanyId(10L);
        d.setName("Deal " + id);
        d.setStageId(stage);
        d.setPrice(new BigDecimal(price));
        d.setCreatedDate(Instant.parse(created));
        return d;
    }

    private static CrmLink link(long subject, ELinkType type, long ref) {
        CrmLink k = new CrmLink();
        k.setSubjectType(ESubject.DEAL);
        k.setSubjectId(subject);
        k.setLinkType(type);
        k.setRefId(ref);
        return k;
    }

    private static Stage stage(long id, long pipeline, int position, String name) {
        Stage s = new Stage();
        s.setId(id);
        s.setPipelineId(pipeline);
        s.setPosition(position);
        s.setName(name);
        return s;
    }

    @Test
    void companyFiguresFromTheCallersCompany() {
        TestLogin.as(10L, "CRM_VIEW");
        when(leads.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(
                lead(1, 7, "2026-01-01T00:00:00Z"), lead(2, 7, "2026-03-01T00:00:00Z"),
                lead(3, 8, "2026-02-01T00:00:00Z")));
        when(deals.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(
                deal(11, 21, "100", "2026-05-01T00:00:00Z"), deal(12, 21, "50", "2026-06-01T00:00:00Z"),
                deal(13, 22, "10", "2026-04-01T00:00:00Z")));
        when(links.findBySubjectTypeAndSubjectIdInAndLinkType(ESubject.DEAL, List.of(11L, 12L, 13L),
                ELinkType.MEMBER)).thenReturn(List.of(link(11, ELinkType.MEMBER, 7), link(12, ELinkType.MEMBER, 9)));
        when(links.findBySubjectTypeAndSubjectIdInAndLinkType(ESubject.DEAL, List.of(11L, 12L, 13L),
                ELinkType.CLIENT)).thenReturn(List.of(link(11, ELinkType.CLIENT, 501),
                link(12, ELinkType.CLIENT, 501), link(13, ELinkType.CLIENT, 502)));
        when(calls.countBySubjectTypeAndSubjectIdInAndDeletedFalse(ESubject.LEAD, List.of(1L, 2L, 3L)))
                .thenReturn(4L);
        when(calls.countBySubjectTypeAndSubjectIdInAndDeletedFalse(ESubject.DEAL, List.of(11L, 12L, 13L)))
                .thenReturn(2L);
        CrmTask t = new CrmTask();
        t.setId(31L);
        t.setSubjectId(2L);
        t.setName("Call back");
        t.setTaskDate(LocalDate.of(2026, 11, 25));
        when(tasks.findTop10BySubjectTypeAndSubjectIdInAndStatusAndTaskDateGreaterThanEqualAndDeletedFalseOrderByTaskDateAscTaskTimeAscIdAsc(
                ESubject.LEAD, List.of(1L, 2L, 3L), ETaskStatus.OPEN, LocalDate.of(2026, 11, 23)))
                .thenReturn(List.of(t));
        when(stages.findByIdIn(any())).thenReturn(List.of(stage(22, 5, 2, "Won"), stage(21, 5, 1, "New")));

        var d = service.dashboard(99L, null, false);

        assertThat(d.scope()).isEqualTo("ALL");
        assertThat(d.leads()).isEqualTo(3);
        assertThat(d.deals()).isEqualTo(3);
        assertThat(d.users()).isEqualTo(3); // 7, 8, 9
        assertThat(d.clients()).isEqualTo(2);
        assertThat(d.calls()).isEqualTo(6);
        assertThat(d.upcomingTasks()).singleElement().satisfies(u -> {
            assertThat(u.leadName()).isEqualTo("Lead 2");
            assertThat(u.leadId()).isEqualTo("2");
        });
        assertThat(d.dealsByStage()).extracting(CrmDashboardService.StageCount::stageName)
                .containsExactly("New", "Won");
        assertThat(d.dealsByStage().get(0).count()).isEqualTo(2);
        assertThat(d.dealsByStage().get(0).value()).isEqualByComparingTo("150");
        assertThat(d.recentDeals()).extracting(CrmDashboardService.RecentDeal::id).containsExactly("12", "11", "13");
        assertThat(d.recentLeads()).extracting(CrmDashboardService.RecentLead::id).containsExactly("2", "3", "1");
        verify(leads, never()).listOwn(anyLong(), any(), any());
    }

    @Test
    void ownScopeUsesTheOwnQueriesAndSkipsEmptyInLists() {
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN");
        when(leads.listOwn(10L, 7L, "kc-7")).thenReturn(List.of(lead(1, 7, "2026-01-01T00:00:00Z")));
        when(deals.listOwn(10L, 7L, "kc-7", ESubject.DEAL, ELinkType.MEMBER)).thenReturn(List.of());

        var d = service.dashboard(null, "Asia/Seoul", false);

        assertThat(d.scope()).isEqualTo("OWN");
        assertThat(d.leads()).isEqualTo(1);
        assertThat(d.deals()).isZero();
        assertThat(d.users()).isEqualTo(1);
        assertThat(d.dealsByStage()).isEmpty();
        verify(leads, never()).findByCompanyIdAndDeletedFalse(anyLong());
        verify(links, never()).findBySubjectTypeAndSubjectIdInAndLinkType(any(), any(), any());
        verify(calls, never()).countBySubjectTypeAndSubjectIdInAndDeletedFalse(eq(ESubject.DEAL), any());
    }

    @Test
    void ownFlagNarrowsAFullViewer() {
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW");
        when(leads.listOwn(10L, 7L, "kc-7")).thenReturn(List.of());
        when(deals.listOwn(10L, 7L, "kc-7", ESubject.DEAL, ELinkType.MEMBER)).thenReturn(List.of());

        assertThat(service.dashboard(null, null, true).scope()).isEqualTo("OWN");
    }

    @Test
    void unknownZoneIsRejected() {
        TestLogin.as(10L, "CRM_VIEW");
        assertThatThrownBy(() -> service.dashboard(null, "Mars/Base", false)).isInstanceOf(ApiException.class);
    }
}
