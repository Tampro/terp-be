package com.terp.erp.notification.feed;

import com.terp.erp.notification.feed.FeedService.UnreadDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pushes new feed rows to the open streams once the consumer's transaction committed (never a rolled-back row). */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedPushListener {
    public static final String EV_NOTIFICATION = "notification";
    public static final String EV_UNREAD = "unread";
    public static final String EV_CHAT = "chat";

    /** The `notification` event body. */
    public record PushDto(String id, String eventType, String title, String linkType, String linkId,
                          java.time.Instant createdAt) {
        static PushDto of(UserNotification n) {
            return new PushDto(String.valueOf(n.getId()), n.getEventType(), n.getTitle(), n.getLinkType(),
                    n.getLinkId(), n.getCreatedAt());
        }
    }

    private final NotificationStreams streams;
    private final UserNotificationRepository repo;

    @TransactionalEventListener
    public void afterCommit(FeedWriter.FeedCreated created) {
        push(created);
    }

    void push(FeedWriter.FeedCreated created) {
        try {
            for (UserNotification n : created.rows()) {
                streams.send(n.getCompanyId(), n.getUserId(), EV_NOTIFICATION, PushDto.of(n));
                streams.send(n.getCompanyId(), n.getUserId(), EV_UNREAD,
                        new UnreadDto(repo.countByCompanyIdAndUserIdAndReadAtIsNull(n.getCompanyId(), n.getUserId())));
            }
            FeedWriter.Chat c = created.chat();
            if (c != null) {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("fromUserId", c.fromUserId());
                body.put("messageId", c.messageId());
                streams.send(c.companyId(), c.toUserId(), EV_CHAT, body);
            }
        } catch (RuntimeException ex) {
            // The rows are committed: a lost push only delays the badge until the next reconnect / poll.
            log.warn("Feed push failed ({})", ex.getClass().getSimpleName());
        }
    }
}
