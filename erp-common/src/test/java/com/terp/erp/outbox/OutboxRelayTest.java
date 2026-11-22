package com.terp.erp.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final OutboxRepository repo = mock(OutboxRepository.class);
    private final OutboxSender sender = mock(OutboxSender.class);
    private final OutboxProperties props = new OutboxProperties(true, null, 2, null, 3, Duration.ofSeconds(5),
            Duration.ofSeconds(12), null);
    private final OutboxRelay relay = new OutboxRelay(repo, sender, props, TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static OutboxEvent event(String id, int attempts) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId(id);
        e.setEventType("support.ticket.created");
        e.setAggregateType("TICKET");
        e.setAggregateId("1");
        e.setPayload("{}");
        e.setAttempts(attempts);
        e.setCreatedAt(NOW);
        e.setNextAttemptAt(NOW);
        return e;
    }

    @Test
    void publishedRowsBecomeSent() {
        OutboxEvent a = event("a", 0);
        when(repo.lockDue(NOW, 2)).thenReturn(List.of(a));

        assertThat(relay.relayBatch()).isEqualTo(1);

        verify(sender).send(a);
        assertThat(a.getStatus()).isEqualTo(EOutboxStatus.SENT);
        assertThat(a.getSentAt()).isEqualTo(NOW);
        verify(repo).saveAll(List.of(a));
    }

    @Test
    void aFailureBacksOffExponentiallyAndKeepsTheRowNew() {
        OutboxEvent first = event("a", 0);
        OutboxEvent second = event("b", 1);
        when(repo.lockDue(NOW, 2)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("broker down")).when(sender).send(any());

        relay.relayBatch();

        assertThat(first.getStatus()).isEqualTo(EOutboxStatus.NEW);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(second.getAttempts()).isEqualTo(2);
        assertThat(second.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(first.getLastError()).isEqualTo("IllegalStateException");
    }

    @Test
    void backoffIsCapped() {
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(12));
        assertThat(relay.backoff(30)).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void theLastAttemptMarksTheRowFailed() {
        OutboxEvent e = event("a", 2);
        when(repo.lockDue(NOW, 2)).thenReturn(List.of(e));
        doThrow(new IllegalStateException("nack")).when(sender).send(e);

        relay.relayBatch();

        assertThat(e.getStatus()).isEqualTo(EOutboxStatus.FAILED);
        assertThat(e.getAttempts()).isEqualTo(3);
        assertThat(e.getSentAt()).isNull();
    }

    @Test
    void oneFailureDoesNotStopTheBatch() {
        OutboxEvent bad = event("bad", 0);
        OutboxEvent good = event("good", 0);
        when(repo.lockDue(NOW, 2)).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("x")).when(sender).send(bad);

        relay.relayBatch();

        assertThat(bad.getStatus()).isEqualTo(EOutboxStatus.NEW);
        assertThat(good.getStatus()).isEqualTo(EOutboxStatus.SENT);
    }

    @Test
    void pollTakesFullBatchesUntilTheOutboxIsDrained() {
        List<List<OutboxEvent>> batches = new ArrayList<>(List.of(
                List.of(event("1", 0), event("2", 0)), List.of(event("3", 0))));
        when(repo.lockDue(eq(NOW), anyInt())).thenAnswer(i -> batches.isEmpty() ? List.of() : batches.remove(0));

        relay.poll();

        verify(repo, times(2)).lockDue(NOW, 2);
        verify(sender, times(3)).send(any());
    }

    @Test
    void anEmptyOutboxSavesNothing() {
        when(repo.lockDue(NOW, 2)).thenReturn(List.of());

        relay.poll();

        verify(repo, never()).saveAll(any());
        verify(sender, never()).send(any());
    }
}
