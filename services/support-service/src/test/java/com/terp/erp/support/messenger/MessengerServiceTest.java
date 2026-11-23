package com.terp.erp.support.messenger;

import com.terp.erp.exception.ApiException;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.client.IamClient;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.messenger.MessengerService.SendForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessengerServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatFavoriteRepository favorites = mock(ChatFavoriteRepository.class);
    private final IamClient iam = mock(IamClient.class);
    private final MediaClient media = mock(MediaClient.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final MessengerService service = new MessengerService(messages, favorites, iam, media,
            Clock.fixed(NOW, ZoneOffset.UTC), outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static ChatMessage msg(long id, long from, long to, boolean seen) {
        ChatMessage m = new ChatMessage();
        m.setId(id);
        m.setCompanyId(10L);
        m.setFromUserId(from);
        m.setToUserId(to);
        m.setBody("hello");
        m.setSeen(seen);
        return m;
    }

    @Test
    void aUserOfAnotherCompanyCannotBeMessaged() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(iam.requireActiveCompanyUser(77L, 10L)).thenThrow(ApiException.badRequest(ESupportError.SUP_029, 77L));

        assertThatThrownBy(() -> service.send(77L, new SendForm("hi", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_029));
        verify(messages, never()).save(any());
    }

    @Test
    void sendChecksThePeerInTheCallersCompanyAndStoresIt() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(messages.save(any())).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            m.setId(1L);
            return m;
        });

        var dto = service.send(6L, new SendForm("  hi  ", null));

        verify(iam).requireActiveCompanyUser(6L, 10L);
        assertThat(dto.fromUserId()).isEqualTo("5");
        assertThat(dto.toUserId()).isEqualTo("6");
        assertThat(dto.body()).isEqualTo("hi");
        assertThat(dto.mine()).isTrue();
        verifyNoInteractions(media);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendPublishesTheChatEventWithoutTheBody() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(messages.save(any())).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            m.setId(42L);
            return m;
        });

        service.send(6L, new SendForm("secret words", null));

        org.mockito.ArgumentCaptor<Object> payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("support.chat.message_sent"), eq("CHAT_MESSAGE"), eq("42"),
                payload.capture());
        java.util.Map<String, Object> p = (java.util.Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("messageId", "42").containsEntry("fromUserId", "5")
                .containsEntry("toUserId", "6").doesNotContainKey("body");
        assertThat(p.values()).noneMatch(v -> String.valueOf(v).contains("secret"));
    }

    @Test
    void refusedMessageIsNotAnnounced() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        assertThatThrownBy(() -> service.send(5L, new SendForm("hi", null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void selfAndEmptyMessagesAreRefusedBeforeAskingIam() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        assertThatThrownBy(() -> service.send(5L, new SendForm("hi", null))).isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_030));
        assertThatThrownBy(() -> service.send(6L, new SendForm("  ", null))).isInstanceOfSatisfying(
                ApiException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_031));
        verifyNoInteractions(iam);
    }

    @Test
    void messagesAreAlwaysReadAsTheCallerSoAnotherPairIsUnreachable() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(messages.between(eq(10L), eq(5L), eq(6L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(msg(2L, 6L, 5L, false), msg(1L, 5L, 6L, true))));

        var page = service.messages(6L, PageRequest.of(0, 20));

        // The repository is only ever asked for (my company, me, peer): user 8's chat with user 6 has no path here.
        verify(messages).between(eq(10L), eq(5L), eq(6L), any(Pageable.class));
        assertThat(page.getContent()).extracting(MessengerService.MessageDto::mine).containsExactly(false, true);
    }

    @Test
    void aSuperAdminOrUserWithoutCompanyHasNoMessenger() {
        TestLogin.superAdmin();
        assertThatThrownBy(() -> service.unread()).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ESupportError.SUP_028);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
        verifyNoInteractions(messages);
    }

    @Test
    void unreadCountAndMarkReadAreScopedToTheCaller() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(messages.countByCompanyIdAndToUserIdAndSeenFalse(10L, 5L)).thenReturn(3L);
        when(messages.markRead(10L, 5L, 6L, NOW)).thenReturn(2);

        assertThat(service.unread().total()).isEqualTo(3);
        assertThat(service.markRead(6L).marked()).isEqualTo(2);
        verify(messages).markRead(10L, 5L, 6L, NOW);
    }

    @Test
    void conversationsCarryLastMessageUnreadCountAndFavorite() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        when(messages.conversations(eq(10L), eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.<Object[]>of(new Object[]{6L, 9L}, new Object[]{7L, 4L})));
        when(messages.findAllById(List.of(9L, 4L))).thenReturn(List.of(msg(9L, 6L, 5L, false), msg(4L, 5L, 7L, true)));
        when(messages.unreadBySender(eq(10L), eq(5L), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{6L, 2L}));
        ChatFavorite f = new ChatFavorite();
        f.setFavoriteUserId(7L);
        when(favorites.findByCompanyIdAndUserIdOrderByIdDesc(10L, 5L)).thenReturn(List.of(f));

        var list = service.conversations(PageRequest.of(0, 20)).getContent();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).userId()).isEqualTo("6");
        assertThat(list.get(0).unreadCount()).isEqualTo(2);
        assertThat(list.get(0).favorite()).isFalse();
        assertThat(list.get(1).lastMessage().id()).isEqualTo("4");
        assertThat(list.get(1).unreadCount()).isZero();
        assertThat(list.get(1).favorite()).isTrue();
    }

    @Test
    void addFavoriteIsIdempotentAndChecksANewPeer() {
        TestLogin.user(5L, 10L, "MESSENGER_USE");
        ChatFavorite existing = new ChatFavorite();
        existing.setFavoriteUserId(6L);
        when(favorites.findByCompanyIdAndUserIdAndFavoriteUserId(10L, 5L, 6L)).thenReturn(Optional.of(existing));
        when(favorites.findByCompanyIdAndUserIdAndFavoriteUserId(10L, 5L, 7L)).thenReturn(Optional.empty());
        when(favorites.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.addFavorite(6L).userId()).isEqualTo("6");
        verify(iam, never()).requireActiveCompanyUser(eq(6L), anyLong());
        assertThat(service.addFavorite(7L).userId()).isEqualTo("7");
        verify(iam).requireActiveCompanyUser(7L, 10L);
    }
}
