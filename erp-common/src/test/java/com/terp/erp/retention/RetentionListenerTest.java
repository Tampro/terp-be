package com.terp.erp.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionListenerTest {
    private final CompanyAnonymizeHandler a = mock(CompanyAnonymizeHandler.class);
    private final CompanyAnonymizeHandler b = mock(CompanyAnonymizeHandler.class);
    private final RetentionConsumerConfig.RetentionListener listener;

    RetentionListenerTest() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        listener = new RetentionConsumerConfig.RetentionListener(
                new ObjectMapper().registerModule(new JavaTimeModule()), List.of(a, b), new TransactionTemplate(tm));
    }

    private static Message msg(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void runsEveryHandlerForTheCompany() {
        listener.onMessage(msg("{\"eventId\":\"e1\",\"eventType\":\"iam.company.anonymize\",\"companyId\":913,"
                + "\"aggregateType\":\"COMPANY\",\"aggregateId\":\"913\",\"data\":{\"companyId\":\"913\"}}"));
        verify(a).anonymize(913L);
        verify(b).anonymize(913L);
    }

    @Test
    void anotherEventOrMalformedBodyIsRejectedWithoutTouchingData() {
        assertThatThrownBy(() -> listener.onMessage(msg("{\"eventId\":\"e2\",\"eventType\":\"iam.user.created\","
                + "\"companyId\":913}"))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onMessage(msg("not json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(a, never()).anonymize(org.mockito.ArgumentMatchers.anyLong());
    }
}
