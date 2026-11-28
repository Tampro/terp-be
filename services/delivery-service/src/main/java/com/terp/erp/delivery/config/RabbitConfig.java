package com.terp.erp.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.delivery.event.OrderEventIntake;
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
 * The durable queue erp.delivery.orders bound to erp.events with order.paid; a message still failing after the
 * listener's retries is dead-lettered through the default exchange to erp.delivery.orders.dlq. The exchange bean comes
 * from the outbox auto-configuration (delivery-service also publishes delivery.status.changed).
 */
@Configuration
public class RabbitConfig {
    @Bean
    Queue deliveryOrdersQueue(DeliveryProperties props) {
        return QueueBuilder.durable(props.ordersQueue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.ordersDeadLetterQueue())
                .build();
    }

    @Bean
    Queue deliveryOrdersDeadLetterQueue(DeliveryProperties props) {
        return QueueBuilder.durable(props.ordersDeadLetterQueue()).build();
    }

    @Bean
    Binding deliveryOrdersBinding(@Qualifier("deliveryOrdersQueue") Queue queue, TopicExchange erpEventsExchange) {
        return BindingBuilder.bind(queue).to(erpEventsExchange).with(OrderEventIntake.ORDER_PAID);
    }

    /** The body is erp-common's EventEnvelope as JSON. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
