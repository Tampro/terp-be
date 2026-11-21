package com.terp.erp.crm.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.ActivityService.Subject;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.LeadService.ConvertForm;
import com.terp.erp.crm.record.LeadService.MoveForm;
import com.terp.erp.crm.record.LeadService.NewClient;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeadServiceTest {
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final SetupService setup = mock(SetupService.class);
    private final ActivityService activity = mock(ActivityService.class);
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final LeadService service = new LeadService(leads, deals, setup, activity, accounting);
    private final Lead lead = new Lead();

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
        lead.setNotes("<p>Met at the expo</p>");
        when(leads.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(lead));
        Stage dealStage = new Stage();
        dealStage.setId(5L);
        dealStage.setPipelineId(1L);
        dealStage.setKind(EStageKind.DEAL);
        dealStage.setName("Initial Contact");
        when(setup.firstStage(1L, EStageKind.DEAL)).thenReturn(dealStage);
        when(deals.save(any(Deal.class))).thenAnswer(inv -> {
            Deal d = inv.getArgument(0);
            d.setId(40L);
            return d;
        });
        when(setup.stageNames(anyCollection())).thenReturn(Map.of());
        when(activity.taskProgress(any(), anyCollection())).thenReturn(Map.of());
        when(activity.linksOf(any(), anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void leadOfAnotherCompanyIsNotFound() {
        TestLogin.as(20L, "CRM_VIEW");
        assertThatThrownBy(() -> service.get(7L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_006);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        assertThatThrownBy(() -> service.convert(7L, new ConvertForm(null, null, 5L, null, Set.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_006);
    }

    @Test
    void convertingCreatesTheClientInAccountingCopiesWhatWasChosenAndHappensOnce() {
        TestLogin.as(10L, "CRM_MANAGE");
        when(accounting.create("Test Prospect Co", "Test Prospect", "prospect@example.com", null))
                .thenReturn(new AccountingClient.Customer("5", "CUST-0001", "Test Prospect Co", "Test Prospect", null));

        var out = service.convert(7L, new ConvertForm(null, new BigDecimal("12000000"), null,
                new NewClient("Test Prospect Co", null, null, null), Set.of("TASKS", "NOTES", "BOGUS")));

        assertThat(out).containsEntry("dealId", "40").containsEntry("customerId", "5");
        assertThat(lead.getConvertedDealId()).isEqualTo(40L);
        verify(deals).save(argThat(d -> d.getName().equals("Wants a 20-seat plan") && d.getStageId().equals(5L)
                && d.getNotes().equals("<p>Met at the expo</p>") && d.getFromLeadId().equals(7L)));
        verify(activity).copy(any(Subject.class), argThat(s -> s.type() == ESubject.DEAL && s.id().equals(40L)),
                eq(Set.of("TASKS", "NOTES")));
        verify(activity).setLinks(argThat(s -> s.type() == ESubject.DEAL), eq(ELinkType.CLIENT), eq(List.of(5L)));
        assertThatThrownBy(() -> service.convert(7L, new ConvertForm(null, null, 5L, null, Set.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_011);
    }

    @Test
    void convertNeedsExactlyOneKindOfClient() {
        TestLogin.as(10L, "CRM_MANAGE");
        assertThatThrownBy(() -> service.convert(7L, new ConvertForm(null, null, null, null, Set.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_018);
        assertThatThrownBy(() -> service.convert(7L, new ConvertForm(null, null, 5L,
                new NewClient("X Co", null, null, null), Set.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_018);
        verify(deals, never()).save(any());
    }

    @Test
    void movingToAStageOfAnotherKindOrPipelineIsRefused() {
        TestLogin.as(10L, "CRM_MANAGE");
        when(setup.requireStageOf(9L, 1L, EStageKind.LEAD))
                .thenThrow(ApiException.badRequest(ECrmError.CRM_008, "lead"));
        assertThatThrownBy(() -> service.move(7L, new MoveForm(9L, null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_008);
    }

    @Test
    void clientsCannotBeLinkedToALead() {
        TestLogin.as(10L, "CRM_MANAGE");
        assertThatThrownBy(() -> service.setLinks(7L, ELinkType.CLIENT, new LeadService.LinksForm(List.of(5L))))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECrmError.CRM_013);
        verify(activity, never()).setLinks(any(), any(), anyList());
    }
}
