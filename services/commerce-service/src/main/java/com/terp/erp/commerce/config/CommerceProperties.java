package com.terp.erp.commerce.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl             IAM, asked for the caller's authorities with the caller's own token
 * @param inventoryServiceUrl       inventory-service: items (prices, taxes) and stock moves, with the caller's token
 * @param accountingServiceUrl      accounting-service: customers, journal validation, with the caller's token
 * @param accountingQueue           queue of the accounting events commerce consumes (journal results, payments)
 * @param accountingDeadLetterQueue where those messages go after the listener's retries
 */
@Validated
@ConfigurationProperties(prefix = "erp.commerce")
public record CommerceProperties(@NotBlank String iamServiceUrl, @NotBlank String inventoryServiceUrl,
                                 @NotBlank String accountingServiceUrl, String accountingQueue,
                                 String accountingDeadLetterQueue) {
    public CommerceProperties {
        accountingQueue = accountingQueue == null || accountingQueue.isBlank() ? "erp.commerce.accounting"
                : accountingQueue;
        accountingDeadLetterQueue = accountingDeadLetterQueue == null || accountingDeadLetterQueue.isBlank()
                ? "erp.commerce.accounting.dlq" : accountingDeadLetterQueue;
    }
}
