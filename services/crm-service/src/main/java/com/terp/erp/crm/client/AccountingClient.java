package com.terp.erp.crm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.config.CrmProperties;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** accounting-service as the caller: customers are the clients of deals and the parties of contracts. */
@Component
public class AccountingClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, String customerCode, String companyName, String contactPerson, String email) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;
    /** (companyId:userId) -> accounting customer id; a failed lookup throws out of the loader, so it is never cached. */
    private final Cache<String, Long> myCustomer = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(10_000).build();

    public AccountingClient(CrmProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.accountingServiceUrl(), mapper, ECrmError.CRM_015, "accounting-service");
        this.mapper = mapper;
    }

    /** The customer of the caller's company; accounting-service answers its own 404 otherwise (relayed). */
    public Customer customer(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/accounting/customers/{id}", id)).path("data");
        return mapper.convertValue(data, Customer.class);
    }

    /** Creates a customer with the caller's token (needs CUSTOMER_MANAGE there). */
    public Customer create(String companyName, String contactPerson, String email, String mobile) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", companyName);
        body.put("contactPerson", contactPerson);
        body.put("email", email);
        if (mobile != null && !mobile.isBlank()) {
            body.put("mobile", mobile);
        }
        body.put("shippingSameAsBilling", true);
        JsonNode data = http.call(c -> c.post().uri("/api/v1/accounting/customers").body(body)).path("data");
        return mapper.convertValue(data, Customer.class);
    }

    /**
     * The accounting customer linked to the caller (client portal), from GET /api/v1/accounting/me/party with the
     * caller's token. Not linked: accounting's 404 ACC_098 is relayed; linked only as a vendor: CRM_022 (404).
     * Never taken from the request.
     */
    public Long myCustomerId() {
        ErpPrincipal p = CurrentUser.get();
        return myCustomer.get(p.companyId() + ":" + p.userId(), k -> {
            JsonNode id = http.call(c -> c.get().uri("/api/v1/accounting/me/party")).path("data").path("customerId");
            if (!(id.isTextual() || id.isNumber()) || id.asText().isBlank()) {
                throw ApiException.notFound(ECrmError.CRM_022);
            }
            return Long.valueOf(id.asText());
        });
    }
}
