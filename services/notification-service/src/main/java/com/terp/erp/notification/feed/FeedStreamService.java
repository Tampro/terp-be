package com.terp.erp.notification.feed;

import com.terp.erp.notification.feed.FeedService.Me;
import com.terp.erp.notification.feed.FeedService.UnreadDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Opens the caller's stream. Its lifetime is the access token's remaining life, capped at 30 min: the stream never
 * outlives the token that authorized it, and the client reconnects with a fresh one.
 */
@Service
@RequiredArgsConstructor
public class FeedStreamService {
    private static final Duration MIN = Duration.ofSeconds(1);

    private final NotificationStreams streams;
    private final UserNotificationRepository repo;
    private final Clock clock;

    public SseEmitter open() {
        Me me = FeedService.me();
        SseEmitter emitter = streams.open(me.companyId(), me.userId(), timeout(tokenExpiry(), Instant.now(clock)));
        streams.send(me.companyId(), me.userId(), FeedPushListener.EV_UNREAD,
                new UnreadDto(repo.countByCompanyIdAndUserIdAndReadAtIsNull(me.companyId(), me.userId())));
        return emitter;
    }

    static Duration timeout(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return NotificationStreams.MAX_LIFETIME;
        }
        Duration left = Duration.between(now, expiresAt);
        if (left.compareTo(MIN) < 0) {
            return MIN;
        }
        return left.compareTo(NotificationStreams.MAX_LIFETIME) > 0 ? NotificationStreams.MAX_LIFETIME : left;
    }

    private static Instant tokenExpiry() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof Jwt jwt ? jwt.getExpiresAt() : null;
    }
}
