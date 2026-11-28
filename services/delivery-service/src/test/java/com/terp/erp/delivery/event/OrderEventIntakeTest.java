package com.terp.erp.delivery.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.delivery.delivery.Delivery;
import com.terp.erp.delivery.delivery.DeliveryRepository;
import com.terp.erp.delivery.delivery.EDeliveryStatus;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventIntakeTest {
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final OrderEventIntake intake = new OrderEventIntake(processed, deliveries, Clock.systemUTC());
    private final ObjectMapper mapper = new ObjectMapper();

    private EventEnvelope paid(String id, Map<String, Object> data) {
        return new EventEnvelope(id, "order.paid", 10L, "ORDER", "100", Instant.now(), null, mapper.valueToTree(data));
    }

    private static Map<String, Object> ids() {
        return Map.of("orderId", "100", "orderNo", "HE-20261128-000001", "storeId", "7", "warehouseId", "245");
    }

    @Test
    void aPaidOrderBecomesOneReadyDelivery() {
        when(deliveries.findByCompanyIdAndOrderId(10L, 100L)).thenReturn(Optional.empty());
        intake.accept(paid("e1", ids()));
        ArgumentCaptor<Delivery> c = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveries).saveAndFlush(c.capture());
        assertThat(c.getValue().getStatus()).isEqualTo(EDeliveryStatus.READY);
        assertThat(c.getValue().getOrderNo()).isEqualTo("HE-20261128-000001");
        assertThat(c.getValue().getCompanyId()).isEqualTo(10L);
    }

    @Test
    void aDuplicateEventOrOrderCreatesNothing() {
        when(processed.existsById("e1")).thenReturn(true);
        intake.accept(paid("e1", ids()));
        when(deliveries.findByCompanyIdAndOrderId(10L, 100L)).thenReturn(Optional.of(new Delivery()));
        intake.accept(paid("e2", ids()));
        verify(deliveries, never()).saveAndFlush(any());
    }

    @Test
    void anEventWithoutIdsIsRejected() {
        assertThatThrownBy(() -> intake.accept(paid("e3", Map.of("orderNo", "HE-1"))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
