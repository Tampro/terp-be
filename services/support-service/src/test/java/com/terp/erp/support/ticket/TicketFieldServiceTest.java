package com.terp.erp.support.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.contact.ContactService;
import com.terp.erp.support.kb.FaqService;
import com.terp.erp.support.kb.KnowledgeService;
import com.terp.erp.support.portal.PortalPageRepository;
import com.terp.erp.support.portal.PortalSettings;
import com.terp.erp.support.portal.PortalSettingsRepository;
import com.terp.erp.support.portal.PortalSettingsService;
import com.terp.erp.support.portal.PublicSupportService;
import com.terp.erp.support.portal.PublicSupportService.PublicTicketForm;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.TicketField.EFieldType;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldInput;
import com.terp.erp.support.ticket.TicketFieldService.FieldForm;
import com.terp.erp.support.ticket.TicketService.TicketForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketFieldServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TicketFieldRepository fieldRepo = mock(TicketFieldRepository.class);
    private final TicketFieldValueRepository valueRepo = mock(TicketFieldValueRepository.class);
    private final TicketFieldService fields = new TicketFieldService(fieldRepo, valueRepo, mapper);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketCategoryRepository categoryRepo = mock(TicketCategoryRepository.class);
    private final TicketNumbers numbers = mock(TicketNumbers.class);
    private final TicketCategoryService categories = new TicketCategoryService(categoryRepo, tickets);
    private final TicketService ticketService = new TicketService(tickets, mock(TicketReplyRepository.class),
            categoryRepo, categories, numbers, new Attachments(mock(MediaClient.class), mapper), fields);
    private final PortalSettingsRepository portalRepo = mock(PortalSettingsRepository.class);
    private final PortalPageRepository pages = mock(PortalPageRepository.class);
    private final PublicSupportService portal = new PublicSupportService(portalRepo, pages,
            new PortalSettingsService(portalRepo, pages, mapper), categories, ticketService, tickets,
            mock(KnowledgeService.class), mock(FaqService.class), mock(ContactService.class), fields);

    private final List<TicketField> active = new ArrayList<>();

    @BeforeEach
    void setUp() {
        active.add(field(1L, "Order number", EFieldType.TEXT, true, null));
        active.add(field(2L, "Amount", EFieldType.NUMBER, false, null));
        active.add(field(3L, "Priority", EFieldType.SELECT, false, "[\"Low\",\"High\"]"));
        active.add(field(4L, "Due", EFieldType.DATE, false, null));
        active.add(field(5L, "Contact", EFieldType.EMAIL, false, null));
        when(fieldRepo.findByCompanyIdAndActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc(10L)).thenReturn(active);
        TicketCategory c = new TicketCategory();
        c.setId(3L);
        c.setCompanyId(10L);
        c.setName("Billing");
        when(categoryRepo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(c));
        when(numbers.next(10L)).thenReturn("TKT-000001");
        when(tickets.save(any())).thenAnswer(i -> {
            Ticket t = i.getArgument(0);
            t.setId(77L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static TicketField field(long id, String label, EFieldType type, boolean required, String options) {
        TicketField f = new TicketField();
        f.setId(id);
        f.setCompanyId(10L);
        f.setLabel(label);
        f.setType(type);
        f.setRequired(required);
        f.setOptionsJson(options);
        f.setSortOrder((int) id);
        return f;
    }

    private static TicketForm form(List<CustomFieldInput> custom) {
        return new TicketForm(EAccountType.CUSTOM, "Test Customer", "customer@example.com", 3L, "Help",
                "<p>Help</p>", null, null, null, custom);
    }

    private static CustomFieldInput in(long id, String v) {
        return new CustomFieldInput(id, v);
    }

    private static void assertCode(Runnable r, ESupportError code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
    }

    @Test
    void adminCreateStoresNormalisedValues() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        ticketService.create(null, form(List.of(in(1, " SO-1 "), in(2, "12.50"), in(3, "High"), in(4, "2026-12-01"),
                in(5, "Ops@Example.com"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketFieldValue>> saved = ArgumentCaptor.forClass(List.class);
        verify(valueRepo).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(TicketFieldValue::getFieldId, TicketFieldValue::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "SO-1"),
                        org.assertj.core.groups.Tuple.tuple(2L, "12.50"),
                        org.assertj.core.groups.Tuple.tuple(3L, "High"),
                        org.assertj.core.groups.Tuple.tuple(4L, "2026-12-01"),
                        org.assertj.core.groups.Tuple.tuple(5L, "ops@example.com"));
        assertThat(saved.getValue()).allMatch(v -> v.getTicketId() == 77L);
    }

    @Test
    void adminCreateRefusesMissingRequiredWrongTypeAndUnknownOption() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        assertCode(() -> ticketService.create(null, form(List.of())), ESupportError.SUP_037);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "   ")))), ESupportError.SUP_037);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(2, "ten")))), ESupportError.SUP_038);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(4, "01/12/2026")))),
                ESupportError.SUP_038);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(5, "not-an-email")))),
                ESupportError.SUP_038);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(3, "Urgent")))),
                ESupportError.SUP_039);
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(1, "b")))), ESupportError.SUP_036);
        verify(tickets, never()).save(any());
    }

    @Test
    void inactiveOrOtherCompanyFieldIsRefused() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        // Field 99 is inactive or belongs to another company: it is not among company 10's active fields.
        assertCode(() -> ticketService.create(null, form(List.of(in(1, "a"), in(99, "x")))), ESupportError.SUP_035);
        verify(tickets, never()).save(any());
    }

    @Test
    void publicCreateValidatesTheSameWay() {
        PortalSettings s = new PortalSettings();
        s.setCompanyId(10L);
        s.setSlug("test-co");
        s.setEnabled(true);
        when(portalRepo.findBySlug("test-co")).thenReturn(Optional.of(s));

        assertCode(() -> portal.createTicket("test-co", new PublicTicketForm("Test Customer", "customer@example.com",
                3L, "Help", "<p>Help</p>", List.of())), ESupportError.SUP_037);
        assertCode(() -> portal.createTicket("test-co", new PublicTicketForm("Test Customer", "customer@example.com",
                3L, "Help", "<p>Help</p>", List.of(in(1, "a"), in(99, "x")))), ESupportError.SUP_035);
        verify(tickets, never()).save(any());

        var created = portal.createTicket("test-co", new PublicTicketForm("Test Customer", "customer@example.com",
                3L, "Help", "<p>Help</p>", List.of(in(1, "SO-9"))));
        assertThat(created.ticketNumber()).isEqualTo("TKT-000001");
        verify(valueRepo).saveAll(anyList());
    }

    @Test
    void fieldFormRules() {
        TestLogin.as(10L, "SUPPORT_SETTINGS_MANAGE");
        when(fieldRepo.save(any())).thenAnswer(i -> {
            TicketField f = i.getArgument(0);
            f.setId(8L);
            return f;
        });
        assertCode(() -> fields.create(null, new FieldForm("Size", null, EFieldType.SELECT, List.of(), true, 6, 1, true)),
                ESupportError.SUP_040);
        assertCode(() -> fields.create(null, new FieldForm("Size", null, EFieldType.TEXT, null, true, 5, 1, true)),
                ESupportError.SUP_041);
        var dto = fields.create(null, new FieldForm(" Size ", "", EFieldType.SELECT, List.of("S", " M ", "S"), true,
                12, null, true));
        assertThat(dto.label()).isEqualTo("Size");
        assertThat(dto.options()).containsExactly("S", "M");
        assertThat(dto.width()).isEqualTo(12);
        assertThat(dto.order()).isZero();
        assertThat(dto.placeholder()).isNull();
    }

    @Test
    void anotherCompanysFieldIsNotFound() {
        TestLogin.as(20L, "SUPPORT_SETTINGS_MANAGE");
        when(fieldRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(active.get(0)));
        assertThatThrownBy(() -> fields.delete(1L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_042);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }

    @Test
    void valuesAreShownWithLabelsInFieldOrder() {
        TicketFieldValue a = new TicketFieldValue();
        a.setTicketId(77L);
        a.setFieldId(3L);
        a.setValue("High");
        TicketFieldValue b = new TicketFieldValue();
        b.setTicketId(77L);
        b.setFieldId(1L);
        b.setValue("SO-1");
        when(valueRepo.findByTicketIdOrderByIdAsc(77L)).thenReturn(List.of(a, b));
        when(fieldRepo.findAllById(anyList())).thenReturn(List.of(active.get(2), active.get(0)));
        assertThat(fields.valuesOf(77L)).extracting(v -> Map.entry(v.label(), v.value()))
                .containsExactly(Map.entry("Order number", "SO-1"), Map.entry("Priority", "High"));
    }
}
