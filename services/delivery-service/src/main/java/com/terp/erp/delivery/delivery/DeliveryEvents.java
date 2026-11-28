package com.terp.erp.delivery.delivery;

import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * delivery.status.changed, written in the transaction of the change (outbox): order-service keeps the latest status on
 * the order for the customer. Ids, status and time only — no rider name or sub, no address.
 */
@Component
@RequiredArgsConstructor
public class DeliveryEvents {
    public static final String STATUS_CHANGED = "delivery.status.changed";

    private final OutboxPublisher outbox;
    private final Clock clock;

    public void changed(Delivery d) {
        outbox.publish(d.getCompanyId(), STATUS_CHANGED, "DELIVERY", d.getId(), Payload.of("orderId", d.getOrderId(),
                "deliveryId", d.getId(), "status", d.getStatus().name(), "at", at(d).toString()));
    }

    /** The time of the current status: what order-service compares so a late event never moves the status back. */
    private Instant at(Delivery d) {
        Instant t = switch (d.getStatus()) {
            case READY -> d.getCreatedDate();
            case ASSIGNED -> d.getAssignedAt();
            case PICKED_UP -> d.getPickedUpAt();
            case DELIVERED -> d.getDeliveredAt();
            case CANCELLED -> d.getCancelledAt();
        };
        return t == null ? clock.instant() : t;
    }
}
