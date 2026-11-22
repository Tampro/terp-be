package com.terp.erp.accounting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.accounting.posting.PostingEventIntake;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The durable queue erp.accounting.postings, bound to erp.events (the exchange bean of the outbox
 * auto-configuration) with exactly commerce.journal.requested. A message still failing after
 * spring.rabbitmq.listener.simple.retry.max-attempts is dead-lettered through the default exchange to its own
 * erp.accounting.postings.dlq (not the shared erp.events.dlq: a posting there is a document still PENDING, which an
 * operator replays once the cause is fixed — the journal is idempotent).
 */
@Configuration
public class RabbitConfig {
    @Bean
    Queue accountingPostingsQueue(AccountingProperties props) {
        return QueueBuilder.durable(props.postingsQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.postingsDeadLetterQueue())
                .build();
    }

    @Bean
    Queue accountingPostingsDeadLetterQueue(AccountingProperties props) {
        return QueueBuilder.durable(props.postingsDeadLetterQueue()).build();
    }

    @Bean
    Binding accountingPostingsBinding(@Qualifier("accountingPostingsQueue") Queue queue,
                                      TopicExchange erpEventsExchange) {
        return BindingBuilder.bind(queue).to(erpEventsExchange).with(PostingEventIntake.EVENT_REQUESTED);
    }

    /** The body is erp-common's EventEnvelope as JSON. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
