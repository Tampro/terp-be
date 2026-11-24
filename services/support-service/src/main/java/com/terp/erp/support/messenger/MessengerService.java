package com.terp.erp.support.messenger;

import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.support.client.IamClient;
import com.terp.erp.support.client.MediaClient;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.Texts;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One-to-one messenger inside a company (ERP manual 18; reference Chatify). REST only, polled by the FE. The caller is
 * always one side of the conversation: every read is "my messages with peer X", never a conversation id, so nobody
 * can read a conversation they are not part of. A peer must be an active user of the caller's company (IAM).
 * Message bodies are never logged, nor put in the support.chat.message_sent event (ids + sender name only): the
 * notification-service pushes it to the recipient's live stream so the FE refreshes the thread and its badge.
 */
@Service
@RequiredArgsConstructor
public class MessengerService {
    public record MessageDto(String id, String fromUserId, String toUserId, String body, String attachmentFileId,
                             String attachmentName, boolean mine, boolean seen, Instant seenAt, Instant createdDate) {
    }

    /** userId = the other user; names come from IAM on the FE side (ids only here). */
    public record ConversationDto(String userId, MessageDto lastMessage, long unreadCount, boolean favorite,
                                  boolean pinned) {
    }

    public record SendForm(
            @Schema(description = "Plain text; null when only a file is sent", example = "Hi, is the report ready?")
            @Size(max = 5000) String body,
            @Schema(description = "media-service file id of the company") Long attachmentFileId) {
    }

    public record ReadResult(int marked) {
    }

    public record UnreadDto(long total) {
    }

    public record FavoriteDto(String userId, Instant createdDate) {
    }

    private record Me(Long userId, Long companyId, String name) {
    }

    static final String EVENT_MESSAGE_SENT = "support.chat.message_sent";

    private final ChatMessageRepository messages;
    private final ChatFavoriteRepository favorites;
    private final ChatPinRepository pins;
    private final IamClient iam;
    private final MediaClient media;
    private final Clock clock;
    private final OutboxPublisher outbox;

    @Transactional(readOnly = true)
    public Page<ConversationDto> conversations(Pageable pageable) {
        Me me = me();
        Page<Object[]> page = messages.conversations(me.companyId(), me.userId(),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        List<Long> peers = page.getContent().stream().map(r -> ((Number) r[0]).longValue()).toList();
        List<Long> lastIds = page.getContent().stream().map(r -> ((Number) r[1]).longValue()).toList();
        Map<Long, ChatMessage> last = messages.findAllById(lastIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<Long, Long> unread = new HashMap<>();
        if (!peers.isEmpty()) {
            for (Object[] r : messages.unreadBySender(me.companyId(), me.userId(), peers)) {
                unread.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
            }
        }
        Set<Long> fav = favoriteIds(me);
        Set<Long> pinned = pins.findByCompanyIdAndUserId(me.companyId(), me.userId()).stream()
                .map(ChatPin::getPeerUserId).collect(Collectors.toSet());
        return page.map(r -> {
            long peer = ((Number) r[0]).longValue();
            ChatMessage m = last.get(((Number) r[1]).longValue());
            return new ConversationDto(String.valueOf(peer), m == null ? null : dto(m, me),
                    unread.getOrDefault(peer, 0L), fav.contains(peer), pinned.contains(peer));
        });
    }

    /** Newest first. Reading does not mark as read: the FE calls markRead when the thread is on screen. */
    @Transactional(readOnly = true)
    public Page<MessageDto> messages(Long peerId, Pageable pageable) {
        Me me = me();
        return messages.between(me.companyId(), me.userId(), peerId,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(m -> dto(m, me));
    }

    @Transactional
    public MessageDto send(Long peerId, SendForm f) {
        Me me = me();
        if (peerId.equals(me.userId())) {
            throw ApiException.badRequest(ESupportError.SUP_030);
        }
        String body = Texts.blankToNull(f.body());
        if (body == null && f.attachmentFileId() == null) {
            throw ApiException.badRequest(ESupportError.SUP_031);
        }
        iam.requireActiveCompanyUser(peerId, me.companyId());
        ChatMessage m = new ChatMessage();
        m.setCompanyId(me.companyId());
        m.setFromUserId(me.userId());
        m.setToUserId(peerId);
        m.setBody(body);
        if (f.attachmentFileId() != null) {
            MediaClient.MediaFile file = media.requireCompanyFile(f.attachmentFileId(), me.companyId());
            m.setAttachmentFileId(f.attachmentFileId());
            m.setAttachmentName(file.name());
        }
        ChatMessage saved = messages.save(m);
        ChatMessage ref = saved == null ? m : saved;
        // TODO(assumption): the sender's login name stands for the display name (the token carries no full name here).
        outbox.publish(me.companyId(), EVENT_MESSAGE_SENT, "CHAT_MESSAGE", String.valueOf(ref.getId()), Payload.of(
                "messageId", ref.getId(), "fromUserId", me.userId(), "toUserId", peerId, "senderName", me.name()));
        return dto(ref, me);
    }

    /** Marks every message from peer to me as seen; answers how many changed. */
    @Transactional
    public ReadResult markRead(Long peerId) {
        Me me = me();
        return new ReadResult(messages.markRead(me.companyId(), me.userId(), peerId, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public UnreadDto unread() {
        Me me = me();
        return new UnreadDto(messages.countByCompanyIdAndToUserIdAndSeenFalse(me.companyId(), me.userId()));
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> favorites() {
        Me me = me();
        return favorites.findByCompanyIdAndUserIdOrderByIdDesc(me.companyId(), me.userId()).stream()
                .map(f -> new FavoriteDto(String.valueOf(f.getFavoriteUserId()), f.getCreatedDate())).toList();
    }

    /** Idempotent. */
    @Transactional
    public FavoriteDto addFavorite(Long peerId) {
        Me me = me();
        if (peerId.equals(me.userId())) {
            throw ApiException.badRequest(ESupportError.SUP_030);
        }
        ChatFavorite f = favorites.findByCompanyIdAndUserIdAndFavoriteUserId(me.companyId(), me.userId(), peerId)
                .orElseGet(() -> {
                    iam.requireActiveCompanyUser(peerId, me.companyId());
                    ChatFavorite n = new ChatFavorite();
                    n.setCompanyId(me.companyId());
                    n.setUserId(me.userId());
                    n.setFavoriteUserId(peerId);
                    return favorites.save(n);
                });
        return new FavoriteDto(String.valueOf(f.getFavoriteUserId()), f.getCreatedDate());
    }

    /** Idempotent: removing a contact that is not a favorite succeeds. */
    @Transactional
    public void removeFavorite(Long peerId) {
        Me me = me();
        favorites.findByCompanyIdAndUserIdAndFavoriteUserId(me.companyId(), me.userId(), peerId)
                .ifPresent(favorites::delete);
    }

    /** Pins my conversation with this user to the top of my list. Idempotent; personal (the peer sees nothing). */
    @Transactional
    public void pin(Long peerId) {
        Me me = me();
        if (peerId.equals(me.userId())) {
            throw ApiException.badRequest(ESupportError.SUP_030);
        }
        if (pins.findByCompanyIdAndUserIdAndPeerUserId(me.companyId(), me.userId(), peerId).isEmpty()) {
            iam.requireActiveCompanyUser(peerId, me.companyId());
            ChatPin p = new ChatPin();
            p.setCompanyId(me.companyId());
            p.setUserId(me.userId());
            p.setPeerUserId(peerId);
            pins.save(p);
        }
    }

    /** Idempotent: unpinning a conversation that is not pinned succeeds. */
    @Transactional
    public void unpin(Long peerId) {
        Me me = me();
        pins.findByCompanyIdAndUserIdAndPeerUserId(me.companyId(), me.userId(), peerId).ifPresent(pins::delete);
    }

    // ----

    /** A user of a company: a super admin (no ERP account) or an account without company has no messenger. */
    private static Me me() {
        ErpPrincipal p = CurrentUser.get();
        if (p.userId() == null || p.companyId() == null) {
            throw ApiException.forbidden(ESupportError.SUP_028);
        }
        return new Me(p.userId(), p.companyId(), p.username());
    }

    private Set<Long> favoriteIds(Me me) {
        return favorites.findByCompanyIdAndUserIdOrderByIdDesc(me.companyId(), me.userId()).stream()
                .map(ChatFavorite::getFavoriteUserId).collect(Collectors.toSet());
    }

    private static MessageDto dto(ChatMessage m, Me me) {
        return new MessageDto(String.valueOf(m.getId()), m.getFromUserId().toString(), m.getToUserId().toString(),
                m.getBody(), m.getAttachmentFileId() == null ? null : m.getAttachmentFileId().toString(),
                m.getAttachmentName(), m.getFromUserId().equals(me.userId()), m.isSeen(), m.getSeenAt(),
                m.getCreatedDate());
    }
}
