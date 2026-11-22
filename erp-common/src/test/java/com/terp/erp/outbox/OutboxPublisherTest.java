package com.terp.erp.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPublisherTest {
    private final OutboxRepository repo = mock(OutboxRepository.class);
    private final Instant now = Instant.parse("2026-11-22T10:00:00Z");
    private final OutboxPublisher publisher = new OutboxPublisher(repo, new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void writesANewDueRow() {
        String id = publisher.publish(7L, "support.ticket.created", "TICKET", 42L, Map.of("ticketNumber", "T-1"));

        ArgumentCaptor<OutboxEvent> c = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo).save(c.capture());
        OutboxEvent e = c.getValue();
        assertThat(e.getEventId()).isEqualTo(id).hasSize(36);
        assertThat(e.getCompanyId()).isEqualTo(7L);
        assertThat(e.getAggregateId()).isEqualTo("42");
        assertThat(e.getPayload()).isEqualTo("{\"ticketNumber\":\"T-1\"}");
        assertThat(e.getStatus()).isEqualTo(EOutboxStatus.NEW);
        assertThat(e.getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    void cannotBeUsedOutsideATransaction() throws NoSuchMethodException {
        Transactional t = OutboxPublisher.class.getMethod("publish", Long.class, String.class, String.class,
                Object.class, Object.class).getAnnotation(Transactional.class);
        assertThat(t.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
