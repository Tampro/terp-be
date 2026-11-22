package com.terp.erp.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * erp.events (topic; the producers declare it too) bound with "#" to the durable queue erp.notification.events.
 * A message the listener still fails on after spring.rabbitmq.listener.simple.retry.max-attempts is rejected without
 * requeue and dead-lettered through the default exchange to erp.events.dlq, where it waits for an operator.
 */
@Configuration
public class RabbitConfig {
    @Bean
    TopicExchange erpEventsExchange(NotificationProperties props) {
        return new TopicExchange(props.exchange(), true, false);
    }

    @Bean
    Queue notificationQueue(NotificationProperties props) {
        return QueueBuilder.durable(props.queue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(props.deadLetterQueue())
                .build();
    }

    @Bean
    Queue deadLetterQueue(NotificationProperties props) {
        return QueueBuilder.durable(props.deadLetterQueue()).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange erpEventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(erpEventsExchange).with("#");
    }

    /** The body is erp-common's EventEnvelope as JSON. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
