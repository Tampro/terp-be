package com.terp.erp.support.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketService.ReplyForm;
import com.terp.erp.support.ticket.TicketService.StatusForm;
import com.terp.erp.support.ticket.TicketService.TicketForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReplyRepository replies = mock(TicketReplyRepository.class);
    private final TicketCategoryRepository categoryRepo = mock(TicketCategoryRepository.class);
    private final TicketNumbers numbers = mock(TicketNumbers.class);
    private final MediaClient media = mock(MediaClient.class);
    private final Attachments attachments = new Attachments(media, new ObjectMapper());
    private final TicketCategoryService categories = new TicketCategoryService(categoryRepo, tickets);
    private final TicketFieldRepository fieldRepo = mock(TicketFieldRepository.class);
    private final TicketFieldValueRepository valueRepo = mock(TicketFieldValueRepository.class);
    private final TicketFieldService fieldService = new TicketFieldService(fieldRepo, valueRepo, new ObjectMapper());
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final TicketService service = new TicketService(tickets, replies, categoryRepo, categories, numbers,
            attachments, fieldService, outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static Ticket ticket(long id, long companyId, EStatus status) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setCompanyId(companyId);
        t.setTicketNumber("TKT-000007");
        t.setAccountType(EAccountType.CUSTOM);
        t.setName("Test Customer");
        t.setEmail("customer@example.com");
        t.setCategoryId(3L);
        t.setSubject("Test subject");
        t.setStatus(status);
        t.setDescription("<p>x</p>");
        t.setNote("internal");
        t.setTrackingTokenHash(TrackingTokens.hash("tok"));
        return t;
    }

    private void category(long id, long companyId) {
        TicketCategory c = new TicketCategory();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setName("Billing");
        when(categoryRepo.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(c));
    }

    private static TicketForm form(Long categoryId) {
        return new TicketForm(EAccountType.STAFF, " Test Staff ", "Staff@Example.com", categoryId, "Printer",
                "<p>Broken<script>alert(1)</script></p>", null, null, "call back", null);
    }

    @Test
    void ticketNumbersArePaddedToSixDigits() {
        assertThat(TicketNumbers.format(1)).isEqualTo("TKT-000001");
        assertThat(TicketNumbers.format(123456)).isEqualTo("TKT-123456");
        assertThat(TicketNumbers.format(1234567)).isEqualTo("TKT-1234567");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createNumbersPerCompanySanitizesAndHashesTheToken() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        category(3L, 10L);
        when(numbers.next(10L)).thenReturn("TKT-000042");
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));

        var created = service.create(null, form(3L));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(saved.capture());
        Ticket t = saved.getValue();
        assertThat(t.getCompanyId()).isEqualTo(10L);
        assertThat(t.getTicketNumber()).isEqualTo("TKT-000042");
        assertThat(t.getStatus()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(t.getEmail()).isEqualTo("staff@example.com");
        assertThat(t.getDescription()).doesNotContain("script");
        assertThat(created.trackingToken()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(t.getTrackingTokenHash()).isEqualTo(TrackingTokens.hash(created.trackingToken()))
                .isNotEqualTo(created.trackingToken());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("support.ticket.created"), eq("TICKET"), any(), payload.capture());
        assertThat((java.util.Map<String, Object>) (java.util.Map<?, ?>) payload.getValue()).containsEntry("ticketNumber", "TKT-000042")
                .containsEntry("recipientEmail", "staff@example.com")
                .doesNotContainValue(created.trackingToken());
    }

    @Test
    void createRefusesAnotherCompanysCategory() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        category(3L, 99L);

        assertThatThrownBy(() -> service.create(null, form(3L)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_005);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(tickets, never()).save(any());
    }

    @Test
    void replyToAClosedTicketIsRefused() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        when(tickets.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(ticket(1L, 10L, EStatus.CLOSED)));

        assertThatThrownBy(() -> service.reply(1L, new ReplyForm("<p>hello</p>", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_006));
        verify(replies, never()).save(any());
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void replyToAnOpenTicketIsSavedAsStaff() {
        TestLogin.as(10L, "SUPPORT_TICKET_MANAGE");
        when(tickets.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(ticket(1L, 10L, EStatus.ON_HOLD)));
        when(replies.save(any())).thenAnswer(i -> i.getArgument(0));

        var r = service.reply(1L, new ReplyForm("<p>hello</p>", List.of()));

        assertThat(r.senderType()).isEqualTo(TicketReply.ESender.STAFF);
        assertThat(r.senderName()).isEqualTo("u5");
        verify(outbox).publish(eq(10L), eq("support.ticket.replied"), eq("TICKET"), eq("1"), any());
    }

    @Test
    void anotherCompanysTicketAnswers404OnEveryByIdAction() {
        TestLogin.as(10L, "SUPPORT_TICKET_VIEW", "SUPPORT_TICKET_MANAGE");
        when(tickets.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(ticket(1L, 20L, EStatus.IN_PROGRESS)));

        for (Runnable call : List.<Runnable>of(() -> service.get(1L),
                () -> service.reply(1L, new ReplyForm("<p>x</p>", null)),
                () -> service.changeStatus(1L, new StatusForm(EStatus.CLOSED)),
                () -> service.update(1L, form(3L)),
                () -> service.delete(1L))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_004);
                assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            });
        }
        verify(replies, never()).save(any());
    }

    @Test
    void statusCountsFillTheTabs() {
        TestLogin.as(10L, "SUPPORT_TICKET_VIEW");
        when(tickets.countByStatus(10L, null, null, null)).thenReturn(List.of(new Object[]{EStatus.IN_PROGRESS, 4L},
                new Object[]{EStatus.CLOSED, 2L}));

        var c = service.statusCounts(null, null, null, null);

        assertThat(c.all()).isEqualTo(6);
        assertThat(c.inProgress()).isEqualTo(4);
        assertThat(c.onHold()).isZero();
        assertThat(c.closed()).isEqualTo(2);
    }

    @Test
    void tooManyAttachmentsAreRefusedBeforeAskingMedia() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        assertThatThrownBy(() -> attachments.resolve(ids, 10L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_008));
        verify(media, never()).requireCompanyFile(any(), any());
    }
}
