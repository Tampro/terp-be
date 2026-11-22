package com.terp.erp.notification.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per consumed eventId, inserted in the transaction that creates its deliveries: a redelivered message finds
 * it and creates nothing (the broker is at-least-once). Keeps the envelope so a retry can rebuild the message; it may
 * hold recipient addresses — never log it. Not a BaseAuditableEntity: the key is the producer's eventId.
 */
@Getter
@Setter
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {
    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "envelope", nullable = false, columnDefinition = "TEXT")
    private String envelope;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
