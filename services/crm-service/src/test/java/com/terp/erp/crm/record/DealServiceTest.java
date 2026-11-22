package com.terp.erp.crm.record;

import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.record.ActivityService.LinkDto;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.outbox.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealServiceTest {
    private final DealRepository deals = mock(DealRepository.class);
    private final ActivityService activity = mock(ActivityService.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final DealService service = new DealService(deals, mock(SetupService.class), activity, outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CrmLink member(long userId) {
        CrmLink l = new CrmLink();
        l.setLinkType(ELinkType.MEMBER);
        l.setRefId(userId);
        return l;
    }

    @Test
    @SuppressWarnings("unchecked")
    void addingMembersPublishesDealAssignedForTheNewOnesOnly() {
        TestLogin.as(10L, "CRM_MANAGE");
        Deal d = new Deal();
        d.setId(40L);
        d.setCompanyId(10L);
        d.setPipelineId(1L);
        d.setName("Test Deal");
        when(deals.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(d));
        when(activity.linksOf(ESubject.DEAL, List.of(40L))).thenReturn(Map.of(40L, List.of(member(12L))));
        when(activity.setLinks(any(), eq(ELinkType.MEMBER), eq(List.of(12L, 13L))))
                .thenReturn(List.of(new LinkDto("12", null), new LinkDto("13", null)));

        service.setLinks(40L, ELinkType.MEMBER, new LeadService.LinksForm(List.of(12L, 13L)));

        verify(outbox).publish(eq(10L), eq("crm.deal.assigned"), eq("DEAL"), eq(40L),
                argThat(x -> List.of("13").equals(((Map<String, Object>) x).get("recipientUserIds"))));
    }

    @Test
    void labelsPublishNothing() {
        TestLogin.as(10L, "CRM_MANAGE");
        Deal d = new Deal();
        d.setId(40L);
        d.setCompanyId(10L);
        when(deals.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(d));

        service.setLinks(40L, ELinkType.LABEL, new LeadService.LinksForm(List.of(3L)));

        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }
}
