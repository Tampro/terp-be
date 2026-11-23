package com.terp.erp.notification.feed;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Open SSE streams per (companyId, userId): the key carries the company, so the same user id in another company
 * never receives a push. In memory, so correct for ONE notification-service instance only.
 * TODO(assumption): with several instances, fan the pushes out through a RabbitMQ fanout exchange with one
 * auto-delete queue per instance, since the instance that consumes an event may not hold the user's stream.
 */
@Component
public class NotificationStreams {
    public static final int MAX_PER_USER = 5;
    public static final Duration MAX_LIFETIME = Duration.ofMinutes(30);

    record Key(Long companyId, Long userId) {
    }

    private final Map<Key, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();

    /** Opens a stream; the user's oldest one is closed when they already hold {@link #MAX_PER_USER}. */
    public SseEmitter open(Long companyId, Long userId, Duration timeout) {
        SseEmitter emitter = create(timeout.toMillis());
        Key key = new Key(companyId, userId);
        Deque<SseEmitter> list = streams.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        list.addLast(emitter);
        while (list.size() > MAX_PER_USER) {
            SseEmitter oldest = list.pollFirst();
            if (oldest != null) {
                oldest.complete();
            }
        }
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            remove(key, emitter);
            emitter.complete();
        });
        emitter.onError(t -> remove(key, emitter));
        return emitter;
    }

    SseEmitter create(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }

    /** Sends to every stream of that user in that company; a broken stream is dropped. */
    public void send(Long companyId, Long userId, String name, Object data) {
        Key key = new Key(companyId, userId);
        Deque<SseEmitter> list = streams.get(key);
        if (list == null) {
            return;
        }
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(name).data(data));
            } catch (IOException | IllegalStateException ex) {
                // Client gone: the container already marked the async request as failed and calls onError itself.
                // completeWithError here would dispatch on that dead request and throw outside this catch.
                remove(key, em);
            }
        }
    }

    /** A comment line every 20 s keeps proxies from closing an idle stream and reveals dead clients. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        for (Map.Entry<Key, Deque<SseEmitter>> e : streams.entrySet()) {
            for (SseEmitter em : e.getValue()) {
                try {
                    em.send(SseEmitter.event().comment("hb"));
                } catch (IOException | IllegalStateException ex) {
                    remove(e.getKey(), em); // see send(): never completeWithError after a failed write
                }
            }
        }
    }

    int count(Long companyId, Long userId) {
        Deque<SseEmitter> list = streams.get(new Key(companyId, userId));
        return list == null ? 0 : list.size();
    }

    private void remove(Key key, SseEmitter emitter) {
        streams.computeIfPresent(key, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
