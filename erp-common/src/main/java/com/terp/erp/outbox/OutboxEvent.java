package com.terp.erp.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One business event written in the same transaction as the change it describes (transactional outbox), then
 * published to the broker by {@link OutboxRelay}. Each producing service has its own outbox_event table (Liquibase
 * change of that service). The payload is the event's data as JSON: ids and display fields, never a secret.
 */
@Getter
@Setter
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Consumers' idempotency key; also the AMQP message id. */
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "company_id", updatable = false)
    private Long companyId;

    /** Also the routing key, e.g. support.ticket.created. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64)
    private String aggregateId;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "trace_id", updatable = false, length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EOutboxStatus status = EOutboxStatus.NEW;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /** Exception class of the last failed publish; never a message text (it could quote the payload). */
    @Column(name = "last_error", length = 100)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
