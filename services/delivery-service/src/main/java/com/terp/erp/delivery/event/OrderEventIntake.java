package com.terp.erp.delivery.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.delivery.delivery.Delivery;
import com.terp.erp.delivery.delivery.DeliveryRepository;
import com.terp.erp.delivery.delivery.EDeliveryStatus;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * order.paid → one READY delivery, once: the processed_event row and the delivery commit together, and (company,
 * order) is unique. No user: the company comes from the envelope (order-service checked the payment).
 */
@Service
@RequiredArgsConstructor
public class OrderEventIntake {
    public static final String ORDER_PAID = "order.paid";

    private final ProcessedEventRepository processed;
    private final DeliveryRepository deliveries;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && processed.existsById(eventId);
    }

    @Transactional(readOnly = true)
    public boolean exists(EventEnvelope e) {
        Long orderId = e == null || e.data() == null ? null : id(e.data().path("orderId"));
        return orderId != null && deliveries.findByCompanyIdAndOrderId(e.companyId(), orderId).isPresent();
    }

    @Transactional
    public void accept(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.companyId() == null || e.data() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed event");
        }
        if (!ORDER_PAID.equals(e.eventType()) || processed.existsById(e.eventId())) {
            return;
        }
        JsonNode d = e.data();
        Long orderId = id(d.path("orderId"));
        Long storeId = id(d.path("storeId"));
        Long warehouseId = id(d.path("warehouseId"));
        String orderNo = d.path("orderNo").asText("");
        if (orderId == null || storeId == null || warehouseId == null || orderNo.isBlank()) {
            throw new AmqpRejectAndDontRequeueException("order.paid without ids");
        }
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setReceivedAt(Instant.now(clock));
        processed.saveAndFlush(pe);
        if (deliveries.findByCompanyIdAndOrderId(e.companyId(), orderId).isPresent()) {
            return;
        }
        Delivery x = new Delivery();
        x.setCompanyId(e.companyId());
        x.setOrderId(orderId);
        x.setOrderNo(orderNo);
        x.setStoreId(storeId);
        x.setWarehouseId(warehouseId);
        x.setStatus(EDeliveryStatus.READY);
        deliveries.saveAndFlush(x);
    }

    private static Long id(JsonNode n) {
        String v = n == null ? "" : n.asText("");
        return v.matches("[0-9]{1,19}") ? Long.valueOf(v) : null;
    }
}
