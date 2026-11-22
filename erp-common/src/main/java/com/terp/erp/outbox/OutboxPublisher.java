package com.terp.erp.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records an event in the caller's transaction: it is published only if the business change commits, and it is
 * not lost if the broker is down (the relay retries). MANDATORY: calling it outside a transaction is a bug and fails.
 * Defined by {@link OutboxAutoConfiguration} (not a @Component: erp-common is component-scanned by every service).
 */
public class OutboxPublisher {
    private final OutboxRepository repo;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxPublisher(OutboxRepository repo, ObjectMapper mapper, Clock clock) {
        this.repo = repo;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * @param eventType     lower-case dotted name, also the routing key: {@code <module>.<aggregate>.<what>}
     * @param payload       serialized with the service's ObjectMapper; ids as strings, no secrets
     * @return the eventId (UUID)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String publish(Long companyId, String eventType, String aggregateType, Object aggregateId,
                          Object payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setCompanyId(companyId);
        e.setEventType(Objects.requireNonNull(eventType, "eventType"));
        e.setAggregateType(Objects.requireNonNull(aggregateType, "aggregateType"));
        e.setAggregateId(String.valueOf(Objects.requireNonNull(aggregateId, "aggregateId")));
        try {
            e.setPayload(mapper.writeValueAsString(payload == null ? java.util.Map.of() : payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("outbox payload of " + eventType + " is not serializable", ex);
        }
        e.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        Instant now = Instant.now(clock);
        e.setStatus(EOutboxStatus.NEW);
        e.setCreatedAt(now);
        e.setNextAttemptAt(now);
        repo.save(e);
        return e.getEventId();
    }
}
