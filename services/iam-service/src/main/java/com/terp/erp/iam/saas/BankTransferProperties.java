package com.terp.erp.iam.saas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The account companies transfer to: one account for all plans, shown with each pending order (set by the PO
 * through SAAS_BANK_* env vars).
 */
@ConfigurationProperties(prefix = "erp.saas.bank")
public record BankTransferProperties(String bankName, String accountNumber, String accountName) {
}
