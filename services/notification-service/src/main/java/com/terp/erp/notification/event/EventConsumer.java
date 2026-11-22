package com.terp.erp.notification.event;

import com.terp.erp.notification.delivery.DeliveryDispatcher;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listener of erp.notification.events. Auto ack: the message is acknowledged once the deliveries are committed
 * (EventIntake); a failure there (IAM down…) throws, the container retries it with back-off, then dead-letters it to
 * erp.events.dlq. Sending happens after the commit and never throws back to the broker: a failed send is the retry
 * job's business, not a redelivery's.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {
    private final EventIntake intake;
    private final DeliveryDispatcher dispatcher;

    @RabbitListener(queues = "${erp.notification.queue}")
    public void onEvent(EventEnvelope event) {
        List<Long> pending;
        try {
            pending = intake.accept(event);
        } catch (DataIntegrityViolationException e) {
            // Two copies of one event at the same time: the other one won the processed_event key.
            log.info("Event {} already processed", event.eventId());
            return;
        }
        try {
            dispatcher.dispatch(pending);
        } catch (RuntimeException e) {
            log.warn("Dispatch of event {} interrupted ({}); the retry job resumes it", event.eventId(),
                    e.getClass().getSimpleName());
        }
    }
}
