package com.terp.erp.support.ticket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.kb.FaqRepository;
import com.terp.erp.support.kb.KbArticleRepository;
import com.terp.erp.support.ticket.Ticket.EStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketStatsServiceTest {
    // 2026-11-22 18:00 UTC = 2026-11-23 01:00 in Ho Chi Minh City
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T18:00:00Z"), ZoneOffset.UTC);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketReplyRepository replies = mock(TicketReplyRepository.class);
    private final TicketCategoryRepository categories = mock(TicketCategoryRepository.class);
    private final KbArticleRepository articles = mock(KbArticleRepository.class);
    private final FaqRepository faqs = mock(FaqRepository.class);
    private final TicketStatsService service = new TicketStatsService(tickets, replies, categories, articles, faqs,
            clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Ticket ticket(long id, EStatus status, Long category, String created, String updated) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setCompanyId(10L);
        t.setStatus(status);
        t.setCategoryId(category);
        t.setCreatedDate(Instant.parse(created));
        t.setUpdatedDate(Instant.parse(updated));
        return t;
    }

    @Test
    void companyFiguresAndFirstResponseAverage() {
        TestLogin.as(10L, "SUPPORT_TICKET_VIEW");
        when(tickets.findByCompanyIdAndDeletedFalse(10L)).thenReturn(List.of(
                ticket(1, EStatus.IN_PROGRESS, 3L, "2026-11-22T17:30:00Z", "2026-11-22T17:30:00Z"), // today in HCM
                ticket(2, EStatus.CLOSED, 3L, "2026-02-10T00:00:00Z", "2026-03-05T00:00:00Z"),
                ticket(3, EStatus.ON_HOLD, null, "2025-12-31T00:00:00Z", "2026-01-01T00:00:00Z")));
        TicketCategory c = new TicketCategory();
        c.setId(3L);
        c.setName("Billing");
        when(categories.findByCompanyIdAndDeletedFalseOrderByNameAsc(10L)).thenReturn(List.of(c));
        when(replies.firstStaffReplies(any())).thenReturn(List.of(
                new Object[]{1L, Instant.parse("2026-11-22T17:40:00Z")}, // 10 min
                new Object[]{2L, Instant.parse("2026-02-10T00:25:00Z")})); // 25 min
        when(articles.countByCompanyIdAndDeletedFalse(10L)).thenReturn(4L);
        when(faqs.countByCompanyIdAndDeletedFalse(10L)).thenReturn(6L);

        var s = service.stats(99L, 2026, null, false);

        assertThat(s.scope()).isEqualTo("ALL");
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.open()).isEqualTo(2);
        assertThat(s.closed()).isEqualTo(1);
        assertThat(s.today()).isEqualTo(1);
        assertThat(s.averageFirstResponseMinutes()).isEqualByComparingTo("17.5");
        assertThat(s.byStatus()).containsEntry(EStatus.ON_HOLD, 1L).containsEntry(EStatus.CLOSED, 1L);
        assertThat(s.byCategory()).anySatisfy(x -> {
            assertThat(x.name()).isEqualTo("Billing");
            assertThat(x.count()).isEqualTo(2);
        }).anySatisfy(x -> assertThat(x.categoryId()).isNull());
        assertThat(s.kbArticles()).isEqualTo(4);
        assertThat(s.faqs()).isEqualTo(6);
        assertThat(s.monthly()).hasSize(12);
        assertThat(s.monthly().get(1).created()).isEqualTo(1); // Feb
        assertThat(s.monthly().get(2).closed()).isEqualTo(1); // Mar
        assertThat(s.monthly().get(10).created()).isEqualTo(1); // Nov
        verify(tickets, never()).listOwn(anyLong(), any(), any());
    }

    @Test
    void ownScopeUsesSubAndEmailAndNoReplyMeansNull() {
        TestLogin.userWithEmail(7L, "kc-7", 10L, "me@example.test", "SUPPORT_TICKET_VIEW_OWN");
        when(tickets.listOwn(10L, "kc-7", "me@example.test")).thenReturn(List.of(
                ticket(5, EStatus.IN_PROGRESS, null, "2026-05-01T00:00:00Z", "2026-05-01T00:00:00Z")));
        when(replies.firstStaffReplies(any())).thenReturn(List.of());

        var s = service.stats(null, null, "Asia/Seoul", false);

        assertThat(s.scope()).isEqualTo("OWN");
        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.total()).isEqualTo(1);
        assertThat(s.averageFirstResponseMinutes()).isNull();
        verify(tickets, never()).findByCompanyIdAndDeletedFalse(anyLong());
    }

    @Test
    void ownFlagNarrowsAFullViewerAndBadZoneIsRejected() {
        TestLogin.userWithEmail(7L, "kc-7", 10L, "me@example.test", "SUPPORT_TICKET_VIEW");
        when(tickets.listOwn(10L, "kc-7", "me@example.test")).thenReturn(List.of());
        var s = service.stats(null, 2026, null, true);
        assertThat(s.scope()).isEqualTo("OWN");
        assertThat(s.averageFirstResponseMinutes()).isNull();
        assertThatThrownBy(() -> service.stats(null, 2026, "Nowhere/X", false)).isInstanceOf(ApiException.class);
    }
}
