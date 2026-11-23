package com.terp.erp.support.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketReply.ESender;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SUPPORT_TICKET_*_OWN and the client portal: mine = created by me or raised with my account's email. */
class TicketOwnScopeTest {
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReplyRepository replies = mock(TicketReplyRepository.class);
    private final TicketCategoryRepository categoryRepo = mock(TicketCategoryRepository.class);
    private final TicketNumbers numbers = mock(TicketNumbers.class);
    private final Attachments attachments = new Attachments(mock(MediaClient.class), new ObjectMapper());
    private final TicketCategoryService categories = new TicketCategoryService(categoryRepo, tickets);
    private final TicketFieldService fields = new TicketFieldService(mock(TicketFieldRepository.class),
            mock(TicketFieldValueRepository.class), new ObjectMapper());
    private final TicketService service = new TicketService(tickets, replies, categoryRepo, categories, numbers,
            attachments, fields, mock(com.terp.erp.outbox.OutboxPublisher.class));
    private final TicketPortalService portal = new TicketPortalService(tickets, service, fields, categories);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Ticket ticket(long id, long company, String createdBy, String email, EStatus status) {
        Ticket t = TicketServiceTest.ticket(id, company, status);
        t.setCreatedBy(createdBy);
        t.setEmail(email);
        when(tickets.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void ownListUsesSubAndEmailAndFullListIsUnaffected() {
        when(tickets.searchOwn(eq(10L), any(), any(), any(), any(), eq("kc-7"), eq("client@example.com"), any()))
                .thenReturn(Page.empty());
        TestLogin.userWithEmail(7L, "kc-7", 10L, "Client@Example.com", "SUPPORT_TICKET_VIEW_OWN");
        service.list(null, null, null, null, null, Pageable.unpaged());
        verify(tickets, never()).search(any(), any(), any(), any(), any(), any());

        when(tickets.search(eq(10L), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        TestLogin.user(7L, "kc-7", 10L, "SUPPORT_TICKET_VIEW");
        service.list(null, null, null, null, null, Pageable.unpaged());
        verify(tickets).search(eq(10L), any(), any(), any(), any(), any());
    }

    @Test
    void anotherUsersTicketAnswers404ForViewOwnAndManageOwn() {
        Ticket other = ticket(1L, 10L, "kc-12", "someone@example.com", EStatus.IN_PROGRESS);
        ticket(2L, 10L, "kc-7", "someone@example.com", EStatus.IN_PROGRESS);
        TestLogin.user(7L, "kc-7", 10L, "SUPPORT_TICKET_VIEW_OWN", "SUPPORT_TICKET_MANAGE_OWN");

        assertThatThrownBy(() -> service.get(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESupportError.SUP_004);
        assertThatThrownBy(() -> service.delete(1L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESupportError.SUP_004);
        assertThat(other.isDeleted()).isFalse();
        assertThat(service.get(2L).ticket().id()).isEqualTo("2");

        TestLogin.user(7L, "kc-7", 10L, "SUPPORT_TICKET_VIEW");
        assertThat(service.get(1L).ticket().id()).isEqualTo("1");
    }

    @Test
    void portalShowsMyEmailsTicketWithoutTheNote() {
        ticket(1L, 10L, null, "client@example.com", EStatus.IN_PROGRESS);
        ticket(2L, 10L, null, "someone@example.com", EStatus.IN_PROGRESS);
        ticket(3L, 20L, null, "client@example.com", EStatus.IN_PROGRESS);
        TestLogin.userWithEmail(7L, "kc-7", 10L, "client@example.com", "PORTAL_CLIENT");

        TicketPortalService.PortalTicket mine = portal.get(1L);
        assertThat(mine.ticket().ticketNumber()).isEqualTo("TKT-000007");
        assertThat(mine.toString()).doesNotContain("internal");
        for (long id : new long[]{2L, 3L}) {
            assertThatThrownBy(() -> portal.get(id))
                    .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESupportError.SUP_004);
        }
    }

    @Test
    void portalListIsMine() {
        Ticket t = ticket(1L, 10L, "kc-7", "client@example.com", EStatus.IN_PROGRESS);
        when(tickets.searchOwn(eq(10L), isNull(), isNull(), isNull(), isNull(), eq("kc-7"), eq("client@example.com"),
                any())).thenReturn(new PageImpl<>(List.of(t)));
        TestLogin.userWithEmail(7L, "kc-7", 10L, "client@example.com", "PORTAL_CLIENT");

        assertThat(portal.list(null, Pageable.unpaged()).getContent()).hasSize(1);
    }

    @Test
    void portalCreateTakesNameAndEmailFromTheToken() {
        TicketCategory c = new TicketCategory();
        c.setId(3L);
        c.setCompanyId(10L);
        c.setName("Billing");
        when(categoryRepo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(c));
        when(numbers.next(10L)).thenReturn("TKT-000009");
        when(tickets.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(9L);
            return t;
        });
        TestLogin.userWithEmail(7L, "kc-7", 10L, "client@example.com", "PORTAL_CLIENT");

        portal.create(new TicketPortalService.PortalTicketForm(3L, "Invoice", "<p>Help</p>", null));

        verify(tickets).save(org.mockito.ArgumentMatchers.argThat(t -> t.getAccountType() == EAccountType.CLIENT
                && "client@example.com".equals(t.getEmail()) && "Test Client 7".equals(t.getName())));
    }

    @Test
    void portalCreateWithoutEmailIsRefused() {
        TestLogin.user(7L, "kc-7", 10L, "PORTAL_CLIENT");

        assertThatThrownBy(() -> portal.create(new TicketPortalService.PortalTicketForm(3L, "x", "<p>x</p>", null)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESupportError.SUP_043);
    }

    @Test
    void portalReplyOnClosedTicketIsRefused() {
        ticket(1L, 10L, "kc-7", "client@example.com", EStatus.CLOSED);
        TestLogin.userWithEmail(7L, "kc-7", 10L, "client@example.com", "PORTAL_CLIENT");

        assertThatThrownBy(() -> portal.reply(1L, new TicketPortalService.PortalReplyForm("<p>Hi</p>")))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ESupportError.SUP_006);
        verify(replies, never()).save(any());
    }

    @Test
    void portalReplyIsFromTheCustomer() {
        ticket(1L, 10L, "kc-7", "client@example.com", EStatus.IN_PROGRESS);
        TestLogin.userWithEmail(7L, "kc-7", 10L, "client@example.com", "PORTAL_CLIENT");

        portal.reply(1L, new TicketPortalService.PortalReplyForm("<p>Hi</p>"));

        verify(replies).save(org.mockito.ArgumentMatchers.argThat(r -> r.getSenderType() == ESender.CUSTOMER));
    }
}
