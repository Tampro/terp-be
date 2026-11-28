package com.terp.erp.order.event;

import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Listener of erp.order.accounting (journal results of SHOP_ORDER postings). Auto ack once the intake commits; a
 * transient failure throws, the container retries with back-off, then the queue dead-letters the message to
 * erp.order.accounting.dlq.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingEventConsumer {
    private final AccountingEventIntake intake;

    @RabbitListener(queues = "${erp.order.accounting-queue}")
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
            // Two copies of one event at the same time: the other one won the processed_event key.
            log.info("Event {} already processed", event.eventId());
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}
