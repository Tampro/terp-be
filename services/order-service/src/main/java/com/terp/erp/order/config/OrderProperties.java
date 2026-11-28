package com.terp.erp.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl       IAM: authorities of a staff caller (staff chain)
 * @param memberServiceUrl    member-service: the caller's membership of a shop (internal, service account)
 * @param storeServiceUrl     store-service: fees and minimums of a store (internal, service account)
 * @param inventoryServiceUrl inventory-service: live price / limit / sold-out of the cart lines (internal, service account)
 * @param customerJwkSetUri   keys of realm erp-customer
 * @param customerIssuer      issuer of realm erp-customer, the only one accepted on /api/v1/shop/**
 * @param encKey              ORDER_ENC_KEY, AES-256-GCM key of the delivery phone kept on orders (base64 of 32 bytes)
 * @param paymentSandbox      true: POST …/pay accepts a chosen outcome (no real PG); false: the endpoint answers 404
 * @param accountingQueue     queue of the journal results order-service consumes (SHOP_ORDER postings)
 * @param accountingDeadLetterQueue where those messages go after the listener's retries
 * @param deliveryQueue       queue of delivery.status.changed (latest delivery status on the order)
 * @param deliveryDeadLetterQueue where those messages go after the listener's retries
 */
@Validated
@ConfigurationProperties(prefix = "erp.order")
public record OrderProperties(@NotBlank String iamServiceUrl, @NotBlank String memberServiceUrl,
                              @NotBlank String storeServiceUrl, @NotBlank String inventoryServiceUrl,
                              @NotBlank String customerJwkSetUri, @NotBlank String customerIssuer,
                              @NotBlank String encKey, boolean paymentSandbox, String accountingQueue,
                              String accountingDeadLetterQueue, String deliveryQueue, String deliveryDeadLetterQueue) {
    public OrderProperties {
        accountingQueue = accountingQueue == null || accountingQueue.isBlank() ? "erp.order.accounting" : accountingQueue;
        accountingDeadLetterQueue = accountingDeadLetterQueue == null || accountingDeadLetterQueue.isBlank()
                ? "erp.order.accounting.dlq" : accountingDeadLetterQueue;
        deliveryQueue = deliveryQueue == null || deliveryQueue.isBlank() ? "erp.order.delivery" : deliveryQueue;
        deliveryDeadLetterQueue = deliveryDeadLetterQueue == null || deliveryDeadLetterQueue.isBlank()
                ? "erp.order.delivery.dlq" : deliveryDeadLetterQueue;
    }
}
