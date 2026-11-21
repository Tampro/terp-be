package com.terp.erp.commerce.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.config.CommerceProperties;

/** accounting-service as the caller: the customer of a document must exist in the caller's company. */
@Component
public class AccountingClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, String customerCode, String companyName, String email) {
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
}
