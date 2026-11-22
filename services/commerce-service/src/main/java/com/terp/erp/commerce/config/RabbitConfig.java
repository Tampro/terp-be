package com.terp.erp.commerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.commerce.event.AccountingEventIntake;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The durable queue erp.commerce.accounting, bound to erp.events (the exchange bean of the outbox
 * auto-configuration) with exactly the accounting routing keys commerce acts on. A message still failing after
 * spring.rabbitmq.listener.simple.retry.max-attempts is rejected without requeue and dead-lettered through the
 * default exchange to its own erp.commerce.accounting.dlq (not the shared erp.events.dlq of notification-service:
 * whoever replays it knows which consumer refused it).
 */
@Configuration
public class RabbitConfig {
    @Bean
    Queue commerceAccountingQueue(CommerceProperties props) {
        return QueueBuilder.durable(props.accountingQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.accountingDeadLetterQueue())
                .build();
    }

    @Bean
    Queue commerceAccountingDeadLetterQueue(CommerceProperties props) {
        return QueueBuilder.durable(props.accountingDeadLetterQueue()).build();
    }

    @Bean
    Declarables commerceAccountingBindings(@Qualifier("commerceAccountingQueue") Queue queue,
                                           TopicExchange erpEventsExchange) {
        return new Declarables(AccountingEventIntake.ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(queue).to(erpEventsExchange).with(key)).toList());
    }

    /** The body is erp-common's EventEnvelope as JSON. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
