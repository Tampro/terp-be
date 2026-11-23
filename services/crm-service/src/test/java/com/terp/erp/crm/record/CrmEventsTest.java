package com.terp.erp.crm.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.InventoryClient;
import com.terp.erp.crm.client.MediaClient;
import com.terp.erp.crm.record.ActivityService.Subject;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.Pipeline;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.outbox.OutboxPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The CRM producers added for notifications: type, aggregate and payload keys of each event. */
class CrmEventsTest {
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final CrmLinkRepository links = mock(CrmLinkRepository.class);
    private final CrmTaskRepository tasks = mock(CrmTaskRepository.class);
    private final CrmEntryRepository entries = mock(CrmEntryRepository.class);
    private final SetupService setup = mock(SetupService.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final ActivityService activityMock = mock(ActivityService.class);

    private final ActivityService activity = new ActivityService(leads, deals, links, tasks,
            mock(CrmCallRepository.class), entries, setup, mock(InventoryClient.class), accounting,
            mock(MediaClient.class), outbox);
    private final LeadService leadService = new LeadService(leads, deals, setup, activityMock, accounting, outbox);
    private final DealService dealService = new DealService(deals, setup, activityMock, outbox);

    private final Lead lead = new Lead();
    private final Deal deal = new Deal();

    @BeforeEach
    void setUp() {
        lead.setId(7L);
        lead.setCompanyId(10L);
        lead.setPipelineId(1L);
        lead.setStageId(2L);
        lead.setName("Test Prospect");
        lead.setEmail("prospect@example.com");
        lead.setSubject("Wants a 20-seat plan");
        lead.setOwnerUserId(12L);
        when(leads.findById(7L)).thenReturn(Optional.of(lead));
        when(leads.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(lead));
        when(leads.save(any(Lead.class))).thenAnswer(i -> { ((Lead) i.getArgument(0)).setId(7L); return i.getArgument(0); });
        deal.setId(40L);
        deal.setCompanyId(10L);
        deal.setPipelineId(1L);
        deal.setName("Test Deal");
        when(deals.findById(40L)).thenReturn(Optional.of(deal));
        when(deals.save(any(Deal.class))).thenAnswer(i -> { ((Deal) i.getArgument(0)).setId(40L); return i.getArgument(0); });
        when(tasks.save(any(CrmTask.class))).thenAnswer(i -> { ((CrmTask) i.getArgument(0)).setId(90L); return i.getArgument(0); });
        when(entries.save(any(CrmEntry.class))).thenAnswer(i -> { ((CrmEntry) i.getArgument(0)).setId(91L); return i.getArgument(0); });

        Pipeline p = new Pipeline();
        p.setId(1L);
        p.setCompanyId(10L);
        when(setup.requirePipeline(1L)).thenReturn(p);
        when(setup.firstStage(1L, EStageKind.LEAD)).thenReturn(stage(2L, EStageKind.LEAD, "New"));
        when(setup.firstStage(1L, EStageKind.DEAL)).thenReturn(stage(5L, EStageKind.DEAL, "Initial Contact"));
        when(setup.stageNames(anyCollection())).thenReturn(Map.of());
        when(activityMock.taskProgress(any(), anyCollection())).thenReturn(Map.of());
        when(activityMock.linksOf(any(), anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leadCreated() {
        TestLogin.as(10L, "CRM_MANAGE");
        leadService.create(new LeadService.LeadForm(1L, null, "Test Prospect", "prospect@example.com",
                "Wants a 20-seat plan", 12L, null, null, null));
        assertThat(captured("crm.lead.created", "LEAD", 7L))
                .containsKeys("leadId", "title", "pipelineId", "name", "subject", "stage", "ownerUserId");
    }

    @Test
    void leadConverted() {
        TestLogin.as(10L, "CRM_MANAGE");
        when(accounting.customer(5L)).thenReturn(new AccountingClient.Customer("5", "CUST-0001", "Test Co", null, null));
        leadService.convert(7L, new LeadService.ConvertForm(null, null, 5L, null, Set.of()));
        assertThat(captured("crm.lead.converted", "LEAD", 7L))
                .containsKeys("leadId", "title", "name", "dealId", "dealName", "customerId", "ownerUserId")
                .containsEntry("dealId", "40");
    }

    @Test
    void dealCreated() {
        TestLogin.as(10L, "CRM_MANAGE");
        dealService.create(new DealService.DealForm(1L, null, "Test Deal", new BigDecimal("1000"), null, null, null));
        assertThat(captured("crm.deal.created", "DEAL", 40L))
                .containsKeys("dealId", "title", "pipelineId", "name", "price", "stage");
    }

    @Test
    void taskOnALeadGoesToTheOwnerAndOnADealToItsMembers() {
        TestLogin.as(10L, "CRM_MANAGE");
        ActivityService.TaskForm form = new ActivityService.TaskForm("Send the price list",
                LocalDate.of(2026, 11, 28), null, null, null);
        activity.addTask(new Subject(ESubject.LEAD, 7L, 10L, 1L), form);
        assertThat(captured("crm.task.created", "CRM_TASK", 90L))
                .containsKeys("taskId", "title", "subjectType", "subjectId", "subjectName", "date", "time", "priority")
                .containsEntry("recipientUserIds", List.of("12"));

        CrmLink member = new CrmLink();
        member.setLinkType(ELinkType.MEMBER);
        member.setRefId(13L);
        CrmLink label = new CrmLink();
        label.setLinkType(ELinkType.LABEL);
        label.setRefId(3L);
        when(links.findBySubjectTypeAndSubjectIdOrderByIdAsc(ESubject.DEAL, 40L)).thenReturn(List.of(member, label));
        org.mockito.Mockito.clearInvocations(outbox);
        activity.addTask(new Subject(ESubject.DEAL, 40L, 10L, 1L), form);
        assertThat(captured("crm.task.created", "CRM_TASK", 90L))
                .containsEntry("subjectName", "Test Deal").containsEntry("recipientUserIds", List.of("13"));
    }

    @Test
    void loggedEmailsAreAddressedToWhatTheUserTyped() {
        TestLogin.as(10L, "CRM_MANAGE");
        ActivityService.EmailForm form = new ActivityService.EmailForm(" buyer@example.com ", "Our offer", "<p>Hi</p>");
        activity.addEmail(new Subject(ESubject.LEAD, 7L, 10L, 1L), form);
        assertThat(captured("crm.lead.emailed", "LEAD", 7L))
                .containsKeys("leadId", "title", "entryId", "subject", "body")
                .containsEntry("recipientEmail", "buyer@example.com");

        activity.addEmail(new Subject(ESubject.DEAL, 40L, 10L, 1L), form);
        assertThat(captured("crm.deal.emailed", "DEAL", 40L))
                .containsKeys("dealId", "title", "entryId", "subject", "body")
                .containsEntry("recipientEmail", "buyer@example.com");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captured(String type, String aggregate, Long aggregateId) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq(type), eq(aggregate), eq(aggregateId), payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    private static Stage stage(Long id, EStageKind kind, String name) {
        Stage s = new Stage();
        s.setId(id);
        s.setPipelineId(1L);
        s.setKind(kind);
        s.setName(name);
        return s;
    }
}
