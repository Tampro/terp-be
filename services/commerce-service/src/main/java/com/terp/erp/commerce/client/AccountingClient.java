package com.terp.erp.commerce.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.config.CommerceProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** accounting-service as the caller: the customer (sales) or vendor (purchase) of a document
 * must exist in the caller's company. */
@Component
public class AccountingClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, String customerCode, String companyName, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vendor(String id, String vendorCode, String companyName, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BankAccount(String id, String holderName, String bankName, String accountNumber, boolean active) {
        public String label() {
            return bankName + " - " + accountNumber;
        }
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public AccountingClient(CommerceProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.accountingServiceUrl(), mapper, ECommerceError.SAL_016, "accounting-service");
        this.mapper = mapper;
    }

    public Customer customer(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/accounting/customers/{id}", id)).path("data");
        return mapper.convertValue(data, Customer.class);
    }

    public BankAccount bankAccount(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/accounting/bank-accounts/{id}", id)).path("data");
        return mapper.convertValue(data, BankAccount.class);
    }

    /**
     * Income into the account and its one journal entry (Dr bank total / Cr revenue total - tax / Cr output VAT tax,
     * Dr COGS / Cr inventory cost), idempotent on (sourceType, sourceId): a retry adds nothing twice.
     */
    public void income(Long bankAccountId, String sourceType, Long sourceId, BigDecimal amount, BigDecimal tax,
                       BigDecimal cost, LocalDate date, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceType", sourceType);
        body.put("sourceId", sourceId);
        body.put("amount", amount);
        body.put("taxAmount", tax);
        body.put("costAmount", cost);
        body.put("date", date.toString());
        body.put("description", description);
        http.call(c -> c.post().uri("/api/v1/accounting/bank-accounts/{id}/income", bankAccountId).body(body));
    }

    /** Amounts of a commerce document by role; accounting-service picks the accounts from its mapping. */
    public record Posting(String sourceType, Long sourceId, LocalDate date, String reference, String description,
                          BigDecimal goodsAmount, BigDecimal serviceAmount, BigDecimal taxAmount,
                          BigDecimal costAmount) {
    }

    /**
     * The document's journal entry (POST /journal-postings), idempotent per (sourceType, sourceId). validateOnly =
     * true checks mapping, closed year (ACC_070) and balance and writes nothing. Errors are relayed.
     */
    public void journal(Posting p, boolean validateOnly) {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceType", p.sourceType());
        body.put("sourceId", p.sourceId());
        body.put("date", p.date().toString());
        body.put("reference", p.reference());
        body.put("description", p.description());
        body.put("goodsAmount", p.goodsAmount());
        body.put("serviceAmount", p.serviceAmount());
        body.put("taxAmount", p.taxAmount());
        body.put("costAmount", p.costAmount());
        body.put("validateOnly", validateOnly);
        http.call(c -> c.post().uri("/api/v1/accounting/journal-postings").body(body));
    }

    public Vendor vendor(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/accounting/vendors/{id}", id)).path("data");
        return mapper.convertValue(data, Vendor.class);
    }
}
