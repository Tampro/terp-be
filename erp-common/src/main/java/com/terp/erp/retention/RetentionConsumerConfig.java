package com.terp.erp.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.outbox.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;

/**
 * Consumer side of retention: a durable queue {@code erp.retention.queue} (e.g. erp.employee.retention) bound to
 * erp.events with exactly {@value #EVENT}, dead-lettered to {@code <queue>.dlq} after the listener's retries. Each
 * message runs every {@link CompanyAnonymizeHandler} of the service in one transaction. Opt-in: set the property in a
 * service that has the outbox (exchange bean) and at least one handler. The body is parsed here (no shared
 * MessageConverter is assumed). Never logs anything but the company id.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "erp.retention.queue")
public class RetentionConsumerConfig {
    public static final String EVENT = "iam.company.anonymize";

    @Bean
    Queue retentionQueue(Environment env) {
        String q = env.getRequiredProperty("erp.retention.queue");
        return QueueBuilder.durable(q).deadLetterExchange("").deadLetterRoutingKey(q + ".dlq").build();
    }

    @Bean
    Queue retentionDeadLetterQueue(Environment env) {
        return QueueBuilder.durable(env.getRequiredProperty("erp.retention.queue") + ".dlq").build();
    }

    @Bean
    Binding retentionBinding(@Qualifier("retentionQueue") Queue queue, TopicExchange erpEventsExchange) {
        return BindingBuilder.bind(queue).to(erpEventsExchange).with(EVENT);
    }

    @Bean
    RetentionListener retentionListener(ObjectMapper mapper, List<CompanyAnonymizeHandler> handlers,
                                        TransactionTemplate tx) {
        return new RetentionListener(mapper, handlers, tx);
    }

    /** Listener of the retention queue. */
    @Slf4j
    public static class RetentionListener {
        private final ObjectMapper mapper;
        private final List<CompanyAnonymizeHandler> handlers;
        private final TransactionTemplate tx;

        RetentionListener(ObjectMapper mapper, List<CompanyAnonymizeHandler> handlers, TransactionTemplate tx) {
            this.mapper = mapper;
            this.handlers = handlers;
            this.tx = tx;
        }

        @RabbitListener(queues = "${erp.retention.queue}")
        public void onMessage(Message message) {
            EventEnvelope e;
            try {
                e = mapper.readValue(message.getBody(), EventEnvelope.class);
            } catch (IOException ex) {
                throw new AmqpRejectAndDontRequeueException("malformed retention event");
            }
            if (e.companyId() == null || !EVENT.equals(e.eventType())) {
                throw new AmqpRejectAndDontRequeueException("unexpected retention event");
            }
            long companyId = e.companyId();
            tx.executeWithoutResult(s -> handlers.forEach(h -> h.anonymize(companyId)));
            log.info("Retention: company {} anonymised by {} handler(s)", companyId, handlers.size());
        }
    }
}
