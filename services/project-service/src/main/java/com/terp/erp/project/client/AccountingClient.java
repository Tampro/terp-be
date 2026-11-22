package com.terp.erp.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.config.ProjectProperties;

import java.util.Objects;

/** accounting-service as the caller: project clients are customers (needs CUSTOMER_VIEW or similar there). */
@Component
public class AccountingClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, String companyId, String customerCode, String companyName) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public AccountingClient(ProjectProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.accountingServiceUrl(), mapper, EProjectError.PRJ_004, "accounting-service");
        this.mapper = mapper;
    }

    /** The customer when it belongs to companyId; accounting's 404 (or another company) answers PRJ_009 (400). */
    public Customer requireCompanyCustomer(Long customerId, Long companyId) {
        Customer c;
        try {
            JsonNode data = http.call(h -> h.get().uri("/api/v1/accounting/customers/{id}", customerId)).path("data");
            c = mapper.convertValue(data, Customer.class);
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EProjectError.PRJ_009, customerId);
            }
            throw e;
        }
        if (c == null || !Objects.equals(c.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EProjectError.PRJ_009, customerId);
        }
        return c;
    }
}
