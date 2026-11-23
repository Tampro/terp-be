package com.terp.erp.iam.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI assistant limits (erp.ai.*).
 *
 * @param requestsPerMinute compose calls allowed per company per minute (in memory, per instance)
 * @param maxOutputTokens   cap sent to the provider on every call
 */
@ConfigurationProperties(prefix = "erp.ai")
public record AiProperties(Integer requestsPerMinute, Integer maxOutputTokens) {
    public AiProperties {
        if (requestsPerMinute == null || requestsPerMinute < 1) {
            requestsPerMinute = 30;
        }
        if (maxOutputTokens == null || maxOutputTokens < 1) {
            maxOutputTokens = 800;
        }
    }
}
