package com.terp.erp.notification.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.TestLogin;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedTest {
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserNotificationRepository repo = mock(UserNotificationRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FeedWriter writer = new FeedWriter(repo, events, clock);
    private final FeedService service = new FeedService(repo, clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private EventEnvelope crmTask(String eventId, Long companyId) {
        ObjectNode data = mapper.createObjectNode().put("title", "Call the customer").put("date", "2026-11-30");
        data.putArray("recipientUserIds").add("7").add("8").add("7").add("not-a-number");
        return new EventEnvelope(eventId, "crm.task.created", companyId, "CRM_TASK", "55", NOW, null, data);
    }

    @SuppressWarnings("unchecked")
    private List<UserNotification> saved() {
        ArgumentCaptor<List<UserNotification>> c = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(c.capture());
        return c.getValue();
    }

    @Test
    void oneRowPerDistinctRecipientWithTitleAndLink() {
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        writer.write(crmTask("e1", 10L), "en");

        List<UserNotification> rows = saved();
        assertThat(rows).extracting(UserNotification::getUserId).containsExactly(7L, 8L);
        assertThat(rows).allSatisfy(n -> {
            assertThat(n.getCompanyId()).isEqualTo(10L);
            assertThat(n.getEventId()).isEqualTo("e1");
            assertThat(n.getTitle()).isEqualTo("New CRM task: Call the customer");
            assertThat(n.getLinkType()).isEqualTo("CRM_TASK");
            assertThat(n.getLinkId()).isEqualTo("55");
            assertThat(n.getReadAt()).isNull();
        });
        verify(events).publishEvent(any(FeedWriter.FeedCreated.class));
    }

    @Test
    void platformEventsAndEventsWithoutRecipientsWriteNothing() {
        writer.write(crmTask("e2", null), "en");
        writer.write(new EventEnvelope("e3", "crm.lead.created", 10L, "LEAD", "1", NOW, null,
                mapper.createObjectNode().put("name", "x")), "en");
        verifyNoInteractions(repo, events);
    }

    @Test
    void chatEventNotifiesTheRecipientWithoutTheBody() {
        when(repo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        ObjectNode data = mapper.createObjectNode().put("messageId", "42").put("fromUserId", "5")
                .put("toUserId", "6").put("senderName", "Test Sender").put("body", "secret words");

        writer.write(new EventEnvelope("e4", FeedTitles.CHAT_EVENT, 10L, "CHAT_MESSAGE", "42", NOW, null, data), "en");

        UserNotification n = saved().get(0);
        assertThat(n.getUserId()).isEqualTo(6L);
        assertThat(n.getTitle()).isEqualTo("New message from Test Sender").doesNotContain("secret");
        assertThat(n.getLinkType()).isEqualTo("CHAT_USER");
        assertThat(n.getLinkId()).isEqualTo("5");
        ArgumentCaptor<FeedWriter.FeedCreated> c = ArgumentCaptor.forClass(FeedWriter.FeedCreated.class);
        verify(events).publishEvent(c.capture());
        assertThat(c.getValue().chat()).isEqualTo(new FeedWriter.Chat(10L, 6L, "5", "42"));
        assertThat(FeedTitles.title(FeedTitles.CHAT_EVENT, "vi", data)).isEqualTo("Tin nhắn mới từ Test Sender");
    }

    @Test
    void pushAfterCommitGoesToEachRecipientAndChatToTheReader() {
        NotificationStreams streams = mock(NotificationStreams.class);
        FeedPushListener listener = new FeedPushListener(streams, repo);
        UserNotification n = new UserNotification();
        n.setId(1L);
        n.setCompanyId(10L);
        n.setUserId(6L);
        n.setEventType(FeedTitles.CHAT_EVENT);
        when(repo.countByCompanyIdAndUserIdAndReadAtIsNull(10L, 6L)).thenReturn(4L);

        listener.push(new FeedWriter.FeedCreated(List.of(n), new FeedWriter.Chat(10L, 6L, "5", "42")));

        verify(streams).send(eq(10L), eq(6L), eq(FeedPushListener.EV_NOTIFICATION), any());
        verify(streams).send(10L, 6L, FeedPushListener.EV_UNREAD, new FeedService.UnreadDto(4));
        verify(streams).send(10L, 6L, FeedPushListener.EV_CHAT,
                java.util.Map.of("fromUserId", "5", "messageId", "42"));
    }

    @Test
    void listAndCountUseTheCallersOwnKeys() {
        TestLogin.user(5L, 10L);
        when(repo.findByCompanyIdAndUserIdAndReadAtIsNull(eq(10L), eq(5L), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(repo.countByCompanyIdAndUserIdAndReadAtIsNull(10L, 5L)).thenReturn(2L);

        service.list(true, PageRequest.of(0, 20));
        assertThat(service.unreadCount().count()).isEqualTo(2L);
        verify(repo, never()).findByCompanyIdAndUserId(any(), any(), any());
    }

    @Test
    void anotherUsersRowIs404() {
        TestLogin.user(5L, 10L);
        when(repo.findByIdAndCompanyIdAndUserId(99L, 10L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ENotificationError.NTF_006));
    }

    @Test
    void markReadKeepsTheFirstReadTimeAndReadAllCountsChanges() {
        TestLogin.user(5L, 10L);
        UserNotification n = new UserNotification();
        n.setId(1L);
        when(repo.findByIdAndCompanyIdAndUserId(1L, 10L, 5L)).thenReturn(Optional.of(n));

        assertThat(service.markRead(1L).read()).isTrue();
        assertThat(n.getReadAt()).isEqualTo(NOW);
        n.setReadAt(NOW.minusSeconds(60));
        service.markRead(1L);
        assertThat(n.getReadAt()).isEqualTo(NOW.minusSeconds(60));

        when(repo.markAllRead(10L, 5L, NOW)).thenReturn(3);
        assertThat(service.markAllRead().marked()).isEqualTo(3);
    }

    @Test
    void superAdminHasNoFeed() {
        TestLogin.superAdmin();
        assertThatThrownBy(service::unreadCount).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ENotificationError.NTF_007));
        verify(repo, never()).countByCompanyIdAndUserIdAndReadAtIsNull(anyLong(), anyLong());
    }
}
