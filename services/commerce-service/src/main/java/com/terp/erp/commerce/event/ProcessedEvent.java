package com.terp.erp.commerce.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per consumed accounting event (key = the producer's eventId), inserted in the transaction of its effect:
 * a redelivered message (the broker is at-least-once) finds it and does nothing. No payload kept (amounts).
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

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
