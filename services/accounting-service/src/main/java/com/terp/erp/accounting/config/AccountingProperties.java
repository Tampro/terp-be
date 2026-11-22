package com.terp.erp.accounting.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param iamServiceUrl           IAM, asked for the caller's authorities with the caller's own token
 * @param commerceServiceUrl      commerce-service: invoices and returns read with the caller's token
 * @param postingsQueue           queue of commerce.journal.requested (journal entries of commerce documents)
 * @param postingsDeadLetterQueue where those messages go after the listener's retries
 */
@Validated
@ConfigurationProperties(prefix = "erp.accounting")
public record AccountingProperties(@NotBlank String iamServiceUrl, @NotBlank String commerceServiceUrl,
                                   String postingsQueue, String postingsDeadLetterQueue) {
    public AccountingProperties {
        postingsQueue = postingsQueue == null || postingsQueue.isBlank() ? "erp.accounting.postings" : postingsQueue;
        postingsDeadLetterQueue = postingsDeadLetterQueue == null || postingsDeadLetterQueue.isBlank()
                ? "erp.accounting.postings.dlq" : postingsDeadLetterQueue;
    }
}
