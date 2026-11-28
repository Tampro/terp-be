package com.terp.erp.order.event;

import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Listener of erp.order.delivery (delivery.status.changed). Auto ack once the intake commits. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {
    private final DeliveryEventIntake intake;

    @RabbitListener(queues = "${erp.order.delivery-queue}")
    public void onEvent(EventEnvelope event) {
        if (event != null && event.traceId() != null) {
            MDC.put(TraceIdFilter.MDC_KEY, event.traceId());
        }
        try {
            intake.accept(event);
        } catch (DataIntegrityViolationException e) {
            if (!intake.isProcessed(event.eventId())) {
                throw e;
            }
            log.info("Event {} already processed", event.eventId());
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}
