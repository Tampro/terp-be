package com.terp.erp.commerce.event;

import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Listener of erp.commerce.accounting (journal results and cleared payments from accounting-service). Auto ack once
 * the intake commits. A transient failure (database down) throws: the container retries with back-off, then the
 * queue dead-letters the message to erp.commerce.accounting.dlq. A business refusal goes to the same DLQ for an
 * operator (the accounting side is already committed). Never logs amounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingEventConsumer {
    private final AccountingEventIntake intake;

    @RabbitListener(queues = "${erp.commerce.accounting-queue}")
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
        } catch (ApiException e) {
            log.warn("Event {} ({}) refused by commerce: {}", event.eventId(), event.eventType(),
                    e.getErrorCode().getCode());
            throw new AmqpRejectAndDontRequeueException("refused " + e.getErrorCode().getCode(), e);
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}
