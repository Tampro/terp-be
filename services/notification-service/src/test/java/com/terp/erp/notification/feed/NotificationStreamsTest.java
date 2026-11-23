package com.terp.erp.notification.feed;

import com.terp.erp.notification.feed.FeedService.UnreadDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStreamsTest {
    /** Records what would go on the wire instead of needing a servlet response. */
    static final class Recording extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean completed;

        Recording(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            StringBuilder sb = new StringBuilder();
            builder.build().forEach(d -> sb.append(d.getData()));
            sent.add(sb.toString());
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }
    }

    private final List<Recording> created = new ArrayList<>();
    private final NotificationStreams streams = new NotificationStreams() {
        @Override
        SseEmitter create(long timeoutMs) {
            Recording r = new Recording(timeoutMs);
            created.add(r);
            return r;
        }
    };

    @Test
    void pushReachesOnlyThatUserOfThatCompany() {
        Recording mine = (Recording) streams.open(10L, 5L, Duration.ofMinutes(5));
        Recording otherCompanySameId = (Recording) streams.open(20L, 5L, Duration.ofMinutes(5));
        Recording colleague = (Recording) streams.open(10L, 6L, Duration.ofMinutes(5));

        streams.send(10L, 5L, FeedPushListener.EV_UNREAD, new UnreadDto(3));

        assertThat(mine.sent).hasSize(1);
        assertThat(mine.sent.get(0)).contains("event:unread");
        assertThat(otherCompanySameId.sent).isEmpty();
        assertThat(colleague.sent).isEmpty();
    }

    @Test
    void aSixthStreamClosesTheOldest() {
        for (int i = 0; i < NotificationStreams.MAX_PER_USER + 1; i++) {
            streams.open(10L, 5L, Duration.ofMinutes(5));
        }
        assertThat(created.get(0).completed).isTrue();
        assertThat(created.subList(1, created.size())).noneMatch(r -> r.completed);
        assertThat(streams.count(10L, 5L)).isEqualTo(NotificationStreams.MAX_PER_USER);
    }

    @Test
    void timeoutFollowsTheTokenExpiryCappedAt30Minutes() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        assertThat(FeedStreamService.timeout(now.plusSeconds(300), now)).isEqualTo(Duration.ofSeconds(300));
        assertThat(FeedStreamService.timeout(now.plusSeconds(7200), now)).isEqualTo(Duration.ofMinutes(30));
        assertThat(FeedStreamService.timeout(now.minusSeconds(5), now)).isEqualTo(Duration.ofSeconds(1));
        assertThat(FeedStreamService.timeout(null, now)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void streamTimeoutIsPassedToTheEmitter() {
        SseEmitter e = streams.open(10L, 5L, Duration.ofSeconds(90));
        assertThat(e.getTimeout()).isEqualTo(90_000L);
    }
}
