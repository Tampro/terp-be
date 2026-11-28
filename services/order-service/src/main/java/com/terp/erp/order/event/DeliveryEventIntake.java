package com.terp.erp.order.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Keeps the latest delivery status on the order, once per event (processed_event). Events can arrive out of order: a
 * status older than the stored one is ignored, so the customer never sees DELIVERED go back to PICKED_UP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryEventIntake {
    public static final String STATUS_CHANGED = "delivery.status.changed";

    private final ProcessedEventRepository processed;
    private final CustomerOrderRepository orders;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && processed.existsById(eventId);
    }

    @Transactional
    public void accept(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.companyId() == null || e.data() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed event");
        }
        if (!STATUS_CHANGED.equals(e.eventType()) || processed.existsById(e.eventId())) {
            return;
        }
        Long orderId = id(e.data().path("orderId"));
        String status = e.data().path("status").asText("");
        Instant at = instant(e.data().path("at"));
        if (orderId == null || status.isBlank() || status.length() > 16 || at == null) {
            throw new AmqpRejectAndDontRequeueException("delivery.status.changed without order, status or time");
        }
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setReceivedAt(Instant.now(clock));
        processed.saveAndFlush(pe);
        CustomerOrder o = orders.findById(orderId).filter(x -> e.companyId().equals(x.getCompanyId())).orElse(null);
        if (o == null) {
            log.warn("Delivery status {} for unknown order {} ignored", e.eventId(), orderId);
            return;
        }
        if (o.getDeliveryStatusAt() != null && at.isBefore(o.getDeliveryStatusAt())) {
            return;
        }
        o.setDeliveryStatus(status);
        o.setDeliveryStatusAt(at);
        orders.save(o);
    }

    private static Long id(JsonNode n) {
        String v = n == null ? "" : n.asText("");
        return v.matches("[0-9]{1,19}") ? Long.valueOf(v) : null;
    }

    private static Instant instant(JsonNode n) {
        try {
            return n == null || n.isNull() || n.isMissingNode() ? null : Instant.parse(n.asText());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
