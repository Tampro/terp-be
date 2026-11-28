package com.terp.erp.delivery.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl          IAM: authorities of the caller, internal rider check (service account)
 * @param orderServiceUrl        order-service: snapshot of an order (internal, service account)
 * @param ordersQueue            queue of order.paid
 * @param ordersDeadLetterQueue  where those messages go after the listener's retries
 */
@Validated
@ConfigurationProperties(prefix = "erp.delivery")
public record DeliveryProperties(@NotBlank String iamServiceUrl, @NotBlank String orderServiceUrl, String ordersQueue,
                                 String ordersDeadLetterQueue) {
    public DeliveryProperties {
        ordersQueue = ordersQueue == null || ordersQueue.isBlank() ? "erp.delivery.orders" : ordersQueue;
        ordersDeadLetterQueue = ordersDeadLetterQueue == null || ordersDeadLetterQueue.isBlank()
                ? "erp.delivery.orders.dlq" : ordersDeadLetterQueue;
    }
}
