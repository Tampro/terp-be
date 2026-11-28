package com.terp.erp.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.order.event.AccountingEventIntake;
import com.terp.erp.order.event.DeliveryEventIntake;
import org.springframework.amqp.core.Binding;
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
 * The durable queue erp.order.accounting, bound to erp.events (the exchange bean of the outbox auto-configuration)
 * with the journal results; a message still failing after the listener's retries is dead-lettered through the default
 * exchange to its own erp.order.accounting.dlq.
 */
@Configuration
public class RabbitConfig {
    @Bean
    Queue orderAccountingQueue(OrderProperties props) {
        return QueueBuilder.durable(props.accountingQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.accountingDeadLetterQueue())
                .build();
    }

    @Bean
    Queue orderAccountingDeadLetterQueue(OrderProperties props) {
        return QueueBuilder.durable(props.accountingDeadLetterQueue()).build();
    }

    @Bean
    Declarables orderAccountingBindings(@Qualifier("orderAccountingQueue") Queue queue, TopicExchange erpEventsExchange) {
        return new Declarables(AccountingEventIntake.ROUTING_KEYS.stream()
                .map(key -> BindingBuilder.bind(queue).to(erpEventsExchange).with(key)).toList());
    }

    /** delivery.status.changed → erp.order.delivery (+ its own DLQ): the latest delivery status on the order. */
    @Bean
    Queue orderDeliveryQueue(OrderProperties props) {
        return QueueBuilder.durable(props.deliveryQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.deliveryDeadLetterQueue())
                .build();
    }

    @Bean
    Queue orderDeliveryDeadLetterQueue(OrderProperties props) {
        return QueueBuilder.durable(props.deliveryDeadLetterQueue()).build();
    }

    @Bean
    Binding orderDeliveryBinding(@Qualifier("orderDeliveryQueue") Queue queue, TopicExchange erpEventsExchange) {
        return BindingBuilder.bind(queue).to(erpEventsExchange).with(DeliveryEventIntake.STATUS_CHANGED);
    }

    /** The body is erp-common's EventEnvelope as JSON. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
