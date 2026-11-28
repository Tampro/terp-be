package com.terp.erp.notification.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.notification.channel.MessageTemplates;
import com.terp.erp.notification.client.OrderInternalClient;
import com.terp.erp.notification.delivery.DeliveryDispatcherFacts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Express customer emails: which code each event gets, who receives it, and what the built-in text says. */
class OrderEmailRoutingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode data(Map<String, Object> m) {
        return mapper.valueToTree(m);
    }

    @Test
    void theDeliveryStatusChoosesTheEmailAndReadyOrAssignedSendNothing() {
        EEventRoute r = EEventRoute.of("delivery.status.changed").orElseThrow();
        assertThat(r.emailCode(data(Map.of("status", "PICKED_UP")))).isEqualTo("ORDER_PICKED_UP");
        assertThat(r.emailCode(data(Map.of("status", "DELIVERED")))).isEqualTo("ORDER_DELIVERED");
        assertThat(r.emailCode(data(Map.of("status", "CANCELLED")))).isEqualTo("ORDER_DELIVERY_CANCELLED");
        assertThat(r.emailCode(data(Map.of("status", "READY")))).isNull();
        assertThat(r.emailCode(data(Map.of("status", "ASSIGNED")))).isNull();
        assertThat(r.customerOrder()).isTrue();
        EEventRoute paid = EEventRoute.of("order.paid").orElseThrow();
        assertThat(paid.emailCode(data(Map.of("orderId", "1")))).isEqualTo("ORDER_PAID");
        assertThat(EEventRoute.of("support.ticket.created").orElseThrow().customerOrder()).isFalse();
    }

    @Test
    void theRecipientIsTheOrderNeverAnAddressInTheEvent() {
        assertThat(EventIntake.orderRecipient(data(Map.of("orderId", "100", "recipientEmail", "x@example.com"))))
                .containsExactly("order:100");
        assertThat(EventIntake.orderRecipient(data(Map.of("orderId", "abc")))).isEmpty();
    }

    @Test
    void theOrderFactsFillTheBuiltInText() {
        JsonNode merged = DeliveryDispatcherFacts.merge(data(Map.of("orderId", "100", "status", "DELIVERED")),
                new OrderInternalClient.Facts("HE-20261128-000001", "Gangseo Store", "Hong", "hong@example.com",
                        new BigDecimal("13000.0000"), "PAID", "DELIVERED"));
        MessageTemplates.Message en = MessageTemplates.email("ORDER_PAID", "en", merged);
        assertThat(en.subject()).isEqualTo("Order HE-20261128-000001 confirmed");
        assertThat(en.body()).contains("Hong").contains("Gangseo Store").contains("13000");
        assertThat(MessageTemplates.email("ORDER_DELIVERED", "vi", merged).subject()).isEqualTo("Đơn HE-20261128-000001 đã giao");
        for (String code : new String[]{"ORDER_PAID", "ORDER_PICKED_UP", "ORDER_DELIVERED", "ORDER_DELIVERY_CANCELLED"}) {
            assertThat(MessageTemplates.hasTemplate(code)).as(code).isTrue();
        }
    }
}
