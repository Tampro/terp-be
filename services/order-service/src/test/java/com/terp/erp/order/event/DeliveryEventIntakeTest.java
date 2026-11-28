package com.terp.erp.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryEventIntakeTest {
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final com.terp.erp.order.mileage.MileageLedger mileage = mock(com.terp.erp.order.mileage.MileageLedger.class);
    private final DeliveryEventIntake intake = new DeliveryEventIntake(processed, orders, Clock.systemUTC(), mileage);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomerOrder order = new CustomerOrder();

    @BeforeEach
    void setUp() {
        order.setId(100L);
        order.setCompanyId(10L);
        when(orders.findById(100L)).thenReturn(Optional.of(order));
    }

    private EventEnvelope changed(String id, String status, String at) {
        return new EventEnvelope(id, "delivery.status.changed", 10L, "DELIVERY", "1", Instant.now(), null,
                mapper.valueToTree(Map.of("orderId", "100", "deliveryId", "1", "status", status, "at", at)));
    }

    @Test
    void keepsTheLatestStatusAndIgnoresAnOlderOne() {
        intake.accept(changed("e1", "PICKED_UP", "2026-11-28T03:10:00Z"));
        assertThat(order.getDeliveryStatus()).isEqualTo("PICKED_UP");
        intake.accept(changed("e2", "ASSIGNED", "2026-11-28T03:05:00Z"));        // late, older
        assertThat(order.getDeliveryStatus()).isEqualTo("PICKED_UP");
        intake.accept(changed("e3", "DELIVERED", "2026-11-28T03:30:00Z"));
        assertThat(order.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(order.getDeliveryStatusAt()).isEqualTo(Instant.parse("2026-11-28T03:30:00Z"));
        verify(mileage).earn(order, Instant.parse("2026-11-28T03:30:00Z"));
        verify(mileage, org.mockito.Mockito.times(1)).earn(any(), any());   // PICKED_UP earned nothing
    }

    @Test
    void duplicatesAndOtherCompaniesChangeNothing() {
        when(processed.existsById("e1")).thenReturn(true);
        intake.accept(changed("e1", "READY", "2026-11-28T03:00:00Z"));
        order.setCompanyId(11L);
        intake.accept(changed("e2", "READY", "2026-11-28T03:00:00Z"));
        assertThat(order.getDeliveryStatus()).isNull();
        verify(orders, never()).save(any());
    }
}
