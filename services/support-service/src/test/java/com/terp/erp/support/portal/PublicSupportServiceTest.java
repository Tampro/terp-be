package com.terp.erp.support.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.contact.ContactService;
import com.terp.erp.support.kb.FaqService;
import com.terp.erp.support.kb.KnowledgeService;
import com.terp.erp.support.portal.PublicSupportService.PublicReplyForm;
import com.terp.erp.support.portal.PublicSupportService.PublicTicketForm;
import com.terp.erp.support.portal.PublicSupportService.TrackForm;
import com.terp.erp.support.ticket.Attachments;
import com.terp.erp.support.ticket.Ticket;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketCategory;
import com.terp.erp.support.ticket.TicketCategoryRepository;
import com.terp.erp.support.ticket.TicketCategoryService;
import com.terp.erp.support.ticket.TicketNumbers;
import com.terp.erp.support.ticket.TicketReply;
import com.terp.erp.support.ticket.TicketReplyRepository;
import com.terp.erp.support.ticket.TicketRepository;
import com.terp.erp.support.ticket.TicketFieldRepository;
import com.terp.erp.support.ticket.TicketFieldService;
import com.terp.erp.support.ticket.TicketFieldValueRepository;
import com.terp.erp.support.ticket.TicketService;
import com.terp.erp.support.ticket.TrackingTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No security context in these tests: the portal runs without a token. */
class PublicSupportServiceTest {
    private static final String SLUG = "test-co";
    private final PortalSettingsRepository settings = mock(PortalSettingsRepository.class);
    private final PortalPageRepository pages = mock(PortalPageRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReplyRepository replies = mock(TicketReplyRepository.class);
    private final TicketCategoryRepository categoryRepo = mock(TicketCategoryRepository.class);
    private final TicketNumbers numbers = mock(TicketNumbers.class);
    private final MediaClient media = mock(MediaClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TicketCategoryService categories = new TicketCategoryService(categoryRepo, tickets);
    final TicketFieldRepository fieldRepo = mock(TicketFieldRepository.class);
    final TicketFieldValueRepository valueRepo = mock(TicketFieldValueRepository.class);
    private final TicketFieldService fieldService = new TicketFieldService(fieldRepo, valueRepo, mapper);
    private final TicketService ticketService = new TicketService(tickets, replies, categoryRepo, categories, numbers,
            new Attachments(media, mapper), fieldService);
    private final PublicSupportService service = new PublicSupportService(settings, pages,
            new PortalSettingsService(settings, pages, mapper), categories, ticketService, tickets,
            mock(KnowledgeService.class), mock(FaqService.class), mock(ContactService.class), fieldService);

    private void portal(boolean enabled) {
        PortalSettings s = new PortalSettings();
        s.setCompanyId(10L);
        s.setSlug(SLUG);
        s.setEnabled(enabled);
        s.setBrandName("Test Co");
        when(settings.findBySlug(SLUG)).thenReturn(Optional.of(s));
    }

    private Ticket stored(EStatus status) {
        Ticket t = new Ticket();
        t.setId(1L);
        t.setCompanyId(10L);
        t.setTicketNumber("TKT-000001");
        t.setName("Test Customer");
        t.setEmail("customer@example.com");
        t.setSubject("Test subject");
        t.setStatus(status);
        t.setNote("internal only");
        t.setTrackingTokenHash(TrackingTokens.hash("the-token"));
        when(tickets.findByCompanyIdAndTicketNumberAndDeletedFalse(10L, "TKT-000001")).thenReturn(Optional.of(t));
        when(tickets.findByCompanyIdAndTrackingTokenHashAndDeletedFalse(10L, TrackingTokens.hash("the-token")))
                .thenReturn(Optional.of(t));
        return t;
    }

    private static void assertCode(Runnable call, ESupportError code, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(status);
        });
    }

    @Test
    void createOnThePortalIsCustomInProgressAndAnswersNumberAndToken() {
        portal(true);
        TicketCategory c = new TicketCategory();
        c.setId(3L);
        c.setCompanyId(10L);
        when(categoryRepo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(c));
        when(numbers.next(10L)).thenReturn("TKT-000001");
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));

        var created = service.createTicket(SLUG, new PublicTicketForm("Test Customer", "customer@example.com", 3L,
                "Help", "<p>Please help</p>", null));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(saved.capture());
        assertThat(saved.getValue().getAccountType()).isEqualTo(Ticket.EAccountType.CUSTOM);
        assertThat(saved.getValue().isFromPortal()).isTrue();
        assertThat(saved.getValue().getAttachments()).isNull();
        assertThat(created.ticketNumber()).isEqualTo("TKT-000001");
        assertThat(created.status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(created.trackingToken()).isNotBlank();
    }

    @Test
    void aDisabledOrUnknownPortalAnswers404() {
        portal(false);
        assertCode(() -> service.info(SLUG), ESupportError.SUP_009, HttpStatus.NOT_FOUND);
        assertCode(() -> service.info("nobody"), ESupportError.SUP_009, HttpStatus.NOT_FOUND);
    }

    @Test
    void trackByNumberAndEmailShowsThePublicConversationWithoutTheNote() {
        portal(true);
        stored(EStatus.IN_PROGRESS);
        TicketReply staff = new TicketReply();
        staff.setSenderType(TicketReply.ESender.STAFF);
        staff.setSenderName("agent.username");
        staff.setDescription("<p>On it</p>");
        when(replies.findByTicketIdOrderByIdAsc(1L)).thenReturn(List.of(staff));

        var view = service.track(SLUG, new TrackForm("tkt-000001", " Customer@Example.com ", null));

        assertThat(view.ticketNumber()).isEqualTo("TKT-000001");
        assertThat(view.status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(view.replies()).singleElement().satisfies(r -> assertThat(r.senderName()).isNull());
        assertThat(view.toString()).doesNotContain("internal only").doesNotContain("agent.username");
    }

    @Test
    void trackWithAWrongEmailOrTokenAnswersTheSameGeneric404() {
        portal(true);
        stored(EStatus.IN_PROGRESS);

        assertCode(() -> service.track(SLUG, new TrackForm("TKT-000001", "other@example.com", null)),
                ESupportError.SUP_010, HttpStatus.NOT_FOUND);
        assertCode(() -> service.track(SLUG, new TrackForm("TKT-999999", "customer@example.com", null)),
                ESupportError.SUP_010, HttpStatus.NOT_FOUND);
        assertCode(() -> service.track(SLUG, new TrackForm(null, null, "wrong-token")),
                ESupportError.SUP_010, HttpStatus.NOT_FOUND);
        assertCode(() -> service.track(SLUG, new TrackForm("TKT-000001", null, null)),
                ESupportError.SUP_010, HttpStatus.NOT_FOUND);
    }

    @Test
    void trackByTokenWorksWithoutTheEmail() {
        portal(true);
        stored(EStatus.ON_HOLD);
        assertThat(service.track(SLUG, new TrackForm(null, null, "the-token")).status()).isEqualTo(EStatus.ON_HOLD);
    }

    @Test
    void customerReplyIsSavedOnAnOpenTicketAndRefusedOnAClosedOne() {
        portal(true);
        Ticket t = stored(EStatus.IN_PROGRESS);
        when(replies.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reply(SLUG, new PublicReplyForm(null, null, "the-token", "<p>More details</p>"));
        ArgumentCaptor<TicketReply> saved = ArgumentCaptor.forClass(TicketReply.class);
        verify(replies).save(saved.capture());
        assertThat(saved.getValue().getSenderType()).isEqualTo(TicketReply.ESender.CUSTOMER);
        assertThat(saved.getValue().getSenderName()).isEqualTo("Test Customer");

        t.setStatus(EStatus.CLOSED);
        assertCode(() -> service.reply(SLUG, new PublicReplyForm("TKT-000001", "customer@example.com", null,
                "<p>again</p>")), ESupportError.SUP_006, HttpStatus.BAD_REQUEST);
    }

    @Test
    void replyWithAWrongEmailSavesNothing() {
        portal(true);
        stored(EStatus.IN_PROGRESS);
        assertCode(() -> service.reply(SLUG, new PublicReplyForm("TKT-000001", "x@example.com", null, "<p>x</p>")),
                ESupportError.SUP_010, HttpStatus.NOT_FOUND);
        verify(replies, never()).save(any());
    }
}
