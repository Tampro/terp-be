package com.terp.erp.delivery.event;

import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** Listener of erp.delivery.orders (order.paid). Auto ack once the intake commits; failures retry, then dead-letter. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {
    private final OrderEventIntake intake;

    @RabbitListener(queues = "${erp.delivery.orders-queue}")
    public void onEvent(EventEnvelope event) {
        if (event != null && event.traceId() != null) {
            MDC.put(TraceIdFilter.MDC_KEY, event.traceId());
        }
        try {
            intake.accept(event);
        } catch (DataIntegrityViolationException e) {
            // Two copies at once: the other one won the processed_event key or the (company, order) key.
            if (!intake.isProcessed(event.eventId()) && !intake.exists(event)) {
                throw e;
            }
            log.info("Event {} already processed", event.eventId());
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}
