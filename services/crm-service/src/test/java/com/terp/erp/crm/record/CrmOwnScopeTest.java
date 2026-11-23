package com.terp.erp.crm.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRM_VIEW_OWN / CRM_MANAGE_OWN: lists narrowed, another user's lead or deal answers 404, full codes unaffected. */
class CrmOwnScopeTest {
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final SetupService setup = mock(SetupService.class);
    private final ActivityService activity = mock(ActivityService.class);
    private final LeadService leadService = new LeadService(leads, deals, setup, activity,
            mock(AccountingClient.class), mock(OutboxPublisher.class));
    private final DealService dealService = new DealService(deals, setup, activity, mock(OutboxPublisher.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Lead lead(long id, Long owner, String createdBy) {
        Lead l = new Lead();
        l.setId(id);
        l.setCompanyId(10L);
        l.setPipelineId(1L);
        l.setStageId(2L);
        l.setName("Test Prospect");
        l.setEmail("prospect@example.com");
        l.setSubject("Test subject");
        l.setOwnerUserId(owner);
        l.setCreatedBy(createdBy);
        when(leads.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(l));
        return l;
    }

    private Deal deal(long id, String createdBy) {
        Deal d = new Deal();
        d.setId(id);
        d.setCompanyId(10L);
        d.setPipelineId(1L);
        d.setStageId(5L);
        d.setName("Test Deal");
        d.setPrice(BigDecimal.TEN);
        d.setCreatedBy(createdBy);
        when(deals.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(d));
        return d;
    }

    private void stubRows() {
        when(setup.stageNames(anyCollection())).thenReturn(Map.of());
        when(activity.taskProgress(any(), anyCollection())).thenReturn(Map.of());
        when(activity.linksOf(any(), anyCollection())).thenReturn(Map.of());
    }

    @Test
    void ownLeadListUsesTheOwnQueryWithMyIds() {
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN");
        stubRows();
        when(leads.searchOwn(eq(10L), any(), any(), any(), any(), any(), any(), eq(7L), eq("kc-7"), any()))
                .thenReturn(Page.empty());

        leadService.search(null, null, null, null, null, null, null, Pageable.unpaged());

        verify(leads, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void fullViewListIsUnaffected() {
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW");
        stubRows();
        when(leads.search(eq(10L), any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        leadService.search(null, null, null, null, null, null, null, Pageable.unpaged());

        verify(leads, never()).searchOwn(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anotherUsersLeadAnswers404ButMineIsShown() {
        stubRows();
        lead(1L, 12L, "kc-12");
        lead(2L, 7L, "kc-12");
        lead(3L, 12L, "kc-7");
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN");

        assertThatThrownBy(() -> leadService.get(1L)).isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_006);
                });
        assertThat(leadService.get(2L).lead().id()).isEqualTo("2");
        assertThat(leadService.get(3L).lead().id()).isEqualTo("3");
    }

    @Test
    void fullViewSeesAnotherUsersLead() {
        stubRows();
        lead(1L, 12L, "kc-12");
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW");

        assertThat(leadService.get(1L).lead().id()).isEqualTo("1");
    }

    @Test
    void manageOwnCannotDeleteAnotherUsersLead() {
        Lead other = lead(1L, 12L, "kc-12");
        TestLogin.user(7L, "kc-7", 10L, "CRM_MANAGE_OWN");

        assertThatThrownBy(() -> leadService.delete(1L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.isDeleted()).isFalse();
    }

    @Test
    void ownLeadBoardShowsOnlyMyCards() {
        stubRows();
        com.terp.erp.crm.setup.Pipeline p = new com.terp.erp.crm.setup.Pipeline();
        p.setId(1L);
        p.setCompanyId(10L);
        when(setup.requirePipeline(1L)).thenReturn(p);
        com.terp.erp.crm.setup.Stage s = new com.terp.erp.crm.setup.Stage();
        s.setId(2L);
        s.setName("New");
        when(setup.stagesOf(1L, com.terp.erp.crm.setup.SetupEntities.EStageKind.LEAD)).thenReturn(List.of(s));
        Lead mine = lead(1L, 7L, "kc-12");
        Lead other = lead(2L, 12L, "kc-12");
        when(leads.findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(10L, 1L))
                .thenReturn(List.of(mine, other));
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN");

        assertThat(leadService.board(1L).get(0).leads()).extracting(LeadService.LeadRow::id).containsExactly("1");
    }

    @Test
    void ownDealListUsesTheMemberQuery() {
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN");
        stubRows();
        when(deals.searchOwn(eq(10L), any(), any(), any(), any(), eq(7L), eq("kc-7"), eq(ESubject.DEAL),
                eq(ELinkType.MEMBER), any())).thenReturn(new PageImpl<>(List.of()));

        dealService.search(null, null, null, null, null, Pageable.unpaged());

        verify(deals, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dealIAmNotMemberOfAnswers404() {
        stubRows();
        Deal d = deal(40L, "kc-12");
        TestLogin.user(7L, "kc-7", 10L, "CRM_VIEW_OWN", "CRM_MANAGE_OWN");
        when(activity.isMine(d)).thenReturn(false);

        assertThatThrownBy(() -> dealService.get(40L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_007);
        assertThatThrownBy(() -> dealService.delete(40L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_007);

        when(activity.isMine(d)).thenReturn(true);
        assertThat(dealService.get(40L).deal().id()).isEqualTo("40");
    }
}
