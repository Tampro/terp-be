package com.terp.erp.outbox;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * The JSON body of every message on the erp.events exchange. Consumers deduplicate on eventId. data = the payload
 * the producer passed to {@link OutboxPublisher#publish}.
 */
public record EventEnvelope(String eventId, String eventType, Long companyId, String aggregateType,
                            String aggregateId, Instant occurredAt, String traceId, JsonNode data) {
    /** AMQP header names (the same values as in the body, readable without parsing it). */
    public static final String H_EVENT_ID = "eventId";
    public static final String H_EVENT_TYPE = "eventType";
    public static final String H_COMPANY_ID = "companyId";
    public static final String H_TRACE_ID = "traceId";
}
