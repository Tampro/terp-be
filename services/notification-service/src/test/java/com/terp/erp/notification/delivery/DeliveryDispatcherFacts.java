package com.terp.erp.notification.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.notification.client.OrderInternalClient;

/** Test access to the package-private fact merge of {@link DeliveryDispatcher}. */
public final class DeliveryDispatcherFacts {
    private DeliveryDispatcherFacts() {
    }

    public static JsonNode merge(JsonNode data, OrderInternalClient.Facts facts) {
        return DeliveryDispatcher.withFacts(data, facts);
    }
}
