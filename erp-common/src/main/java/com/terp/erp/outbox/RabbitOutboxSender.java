package com.terp.erp.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Persistent JSON message to the topic exchange, routing key = event type, then waits for the publisher confirm
 * (needs spring.rabbitmq.publisher-confirm-type=simple). A nack or timeout throws, so the relay retries the row.
 */
public class RabbitOutboxSender implements OutboxSender {
    private final RabbitTemplate template;
    private final ObjectMapper mapper;
    private final OutboxProperties props;

    public RabbitOutboxSender(RabbitTemplate template, ObjectMapper mapper, OutboxProperties props) {
        this.template = template;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public void send(OutboxEvent e) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(new EventEnvelope(e.getEventId(), e.getEventType(), e.getCompanyId(),
                    e.getAggregateType(), e.getAggregateId(), e.getCreatedAt(), e.getTraceId(),
                    mapper.readTree(e.getPayload())));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("unreadable outbox payload", ex);
        }
        MessageProperties p = new MessageProperties();
        p.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        p.setContentEncoding(StandardCharsets.UTF_8.name());
        p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        p.setMessageId(e.getEventId());
        p.setType(e.getEventType());
        p.setHeader(EventEnvelope.H_EVENT_ID, e.getEventId());
        p.setHeader(EventEnvelope.H_EVENT_TYPE, e.getEventType());
        if (e.getCompanyId() != null) {
            p.setHeader(EventEnvelope.H_COMPANY_ID, String.valueOf(e.getCompanyId()));
        }
        if (e.getTraceId() != null) {
            p.setHeader(EventEnvelope.H_TRACE_ID, e.getTraceId());
        }
        Message m = new Message(body, p);
        long timeout = props.confirmTimeout().toMillis();
        template.invoke(ops -> {
            ops.send(props.exchange(), e.getEventType(), m);
            ops.waitForConfirmsOrDie(timeout);
            return Boolean.TRUE;
        });
    }
}
