package com.terp.erp.notification.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the feed rows of one company event inside the consumer's transaction (the processed_event row makes it
 * once per eventId; the unique key backs that up) and announces them, so the streams are fed only after commit.
 * Recipients: data.recipientUserIds; for the chat event data.toUserId. Nothing for a platform event.
 */
@Component
@RequiredArgsConstructor
public class FeedWriter {
    /** Published inside the transaction; {@link FeedPushListener} handles it after commit. */
    public record FeedCreated(List<UserNotification> rows, Chat chat) {
    }

    /** The chat push: who wrote, which message; never the body. */
    public record Chat(Long companyId, Long toUserId, String fromUserId, String messageId) {
    }

    private final UserNotificationRepository repo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public List<UserNotification> write(EventEnvelope e, String language) {
        if (e.companyId() == null || e.data() == null) {
            return List.of();
        }
        boolean chat = FeedTitles.CHAT_EVENT.equals(e.eventType());
        Set<Long> users = recipients(e.data(), chat);
        if (users.isEmpty()) {
            return List.of();
        }
        String title = FeedTitles.title(e.eventType(), language, e.data());
        String from = chat ? e.data().path("fromUserId").asText(null) : null;
        Instant now = Instant.now(clock);
        List<UserNotification> rows = new ArrayList<>();
        for (Long u : users) {
            UserNotification n = new UserNotification();
            n.setCompanyId(e.companyId());
            n.setUserId(u);
            n.setEventId(e.eventId());
            n.setEventType(e.eventType());
            n.setTitle(title);
            n.setLinkType(chat ? "CHAT_USER" : e.aggregateType());
            n.setLinkId(chat ? from : e.aggregateId());
            n.setCreatedAt(now);
            rows.add(n);
        }
        List<UserNotification> saved = repo.saveAll(rows);
        Chat c = chat ? new Chat(e.companyId(), users.iterator().next(), from,
                e.data().path("messageId").asText(null)) : null;
        events.publishEvent(new FeedCreated(saved == null ? rows : saved, c));
        return saved == null ? rows : saved;
    }

    static Set<Long> recipients(JsonNode data, boolean chat) {
        Set<Long> out = new LinkedHashSet<>();
        if (chat) {
            add(out, data.path("toUserId").asText(""));
        } else {
            for (JsonNode id : data.path("recipientUserIds")) {
                add(out, id.asText(""));
            }
        }
        return out;
    }

    private static void add(Set<Long> out, String v) {
        if (v.matches("[0-9]{1,18}")) {
            out.add(Long.valueOf(v));
        }
    }
}
