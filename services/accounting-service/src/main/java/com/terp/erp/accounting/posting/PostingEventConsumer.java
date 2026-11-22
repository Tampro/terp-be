package com.terp.erp.accounting.posting;

import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Listener of erp.accounting.postings. A business refusal of the entry (ApiException 4xx: closed year ACC_070,
 * unmapped account ACC_017, nothing to post ACC_019) is permanent: it is answered with accounting.journal.failed and
 * the message is acknowledged. Anything else (database down…) is transient: it is thrown, the container retries
 * with back-off, then the queue dead-letters the message to erp.accounting.postings.dlq — no "failed" event, the
 * document stays PENDING until an operator replays it. Never logs amounts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingEventConsumer {
    private final PostingEventIntake intake;

    @RabbitListener(queues = "${erp.accounting.postings-queue}")
    public void onEvent(EventEnvelope event) {
        if (event != null && event.traceId() != null) {
            MDC.put(TraceIdFilter.MDC_KEY, event.traceId());
        }
        try {
            intake.post(event);
        } catch (DataIntegrityViolationException e) {
            if (!intake.isProcessed(event.eventId())) {
                throw e;
            }
            // Two copies of one event at the same time: the other one won the processed_event key.
            log.info("Event {} already processed", event.eventId());
        } catch (ApiException e) {
            if (!e.getStatus().is4xxClientError()) {
                throw e;
            }
            String code = e.getErrorCode().getCode();
            log.warn("Journal of event {} refused: {}", event.eventId(), code);
            intake.fail(event, code);
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}
