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
 * erp.events with exactly {@code erp.retention.event} (default {@value #EVENT}; accounting and commerce use
 * {@value #FINANCE_EVENT}, sent 10 years after the deletion), dead-lettered to {@code <queue>.dlq} after the listener's retries. Each
 * message runs every {@link CompanyAnonymizeHandler} of the service in one transaction. Opt-in: set the property in a
 * service with spring-boot-starter-amqp and at least one handler. The body is parsed here (no shared
 * MessageConverter is assumed). Never logs anything but the company id.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "erp.retention.queue")
public class RetentionConsumerConfig {
    public static final String EVENT = "iam.company.anonymize";
    public static final String FINANCE_EVENT = "iam.company.anonymize-finance";

    @Bean
    Queue retentionQueue(Environment env) {
        String q = env.getRequiredProperty("erp.retention.queue");
        return QueueBuilder.durable(q).deadLetterExchange("").deadLetterRoutingKey(q + ".dlq").build();
    }

    @Bean
    Queue retentionDeadLetterQueue(Environment env) {
        return QueueBuilder.durable(env.getRequiredProperty("erp.retention.queue") + ".dlq").build();
    }

    /**
     * The shared exchange, declared here too (same name and arguments as the outbox's, so declaring it twice is a
     * no-op) because services without the outbox (attendance, payroll, document, asset, media) only consume.
     */
    @Bean
    TopicExchange retentionEventsExchange(Environment env) {
        return new TopicExchange(env.getProperty("erp.outbox.exchange", "erp.events"), true, false);
    }

    @Bean
    Binding retentionBinding(@Qualifier("retentionQueue") Queue queue,
                             @Qualifier("retentionEventsExchange") TopicExchange exchange, Environment env) {
        return BindingBuilder.bind(queue).to(exchange).with(event(env));
    }

    static String event(Environment env) {
        return env.getProperty("erp.retention.event", EVENT);
    }

    @Bean
    RetentionListener retentionListener(ObjectMapper mapper, List<CompanyAnonymizeHandler> handlers,
                                        TransactionTemplate tx, Environment env) {
        return new RetentionListener(mapper, handlers, tx, event(env));
    }

    /** Listener of the retention queue. */
    @Slf4j
    public static class RetentionListener {
        private final ObjectMapper mapper;
        private final List<CompanyAnonymizeHandler> handlers;
        private final TransactionTemplate tx;
        private final String event;

        RetentionListener(ObjectMapper mapper, List<CompanyAnonymizeHandler> handlers, TransactionTemplate tx,
                          String event) {
            this.mapper = mapper;
            this.handlers = handlers;
            this.tx = tx;
            this.event = event;
        }

        @RabbitListener(queues = "${erp.retention.queue}")
        public void onMessage(Message message) {
            EventEnvelope e;
            try {
                e = mapper.readValue(message.getBody(), EventEnvelope.class);
            } catch (IOException ex) {
                throw new AmqpRejectAndDontRequeueException("malformed retention event");
            }
            if (e.companyId() == null || !event.equals(e.eventType())) {
                throw new AmqpRejectAndDontRequeueException("unexpected retention event");
            }
            long companyId = e.companyId();
            tx.executeWithoutResult(s -> handlers.forEach(h -> h.anonymize(companyId)));
            log.info("Retention: company {} anonymised by {} handler(s)", companyId, handlers.size());
        }
    }
}
