package com.terp.erp.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.config.ProjectProperties;

import java.time.Duration;
import java.util.Objects;

/** accounting-service as the caller: project clients are customers (needs CUSTOMER_VIEW or similar there). */
@Component
public class AccountingClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, String companyId, String customerCode, String companyName) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;
    /** (companyId:userId) -> accounting customer id; a failed lookup throws out of the loader, so it is never cached. */
    private final Cache<String, Long> myCustomer = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(10_000).build();

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

    /**
     * The accounting customer linked to the caller (client portal), from GET /api/v1/accounting/me/party with the
     * caller's token. Not linked: accounting's 404 ACC_098 is relayed; linked only as a vendor: PRJ_026 (404).
     * Never taken from the request.
     */
    public Long myCustomerId() {
        ErpPrincipal p = CurrentUser.get();
        return myCustomer.get(p.companyId() + ":" + p.userId(), k -> {
            JsonNode id = http.call(c -> c.get().uri("/api/v1/accounting/me/party")).path("data").path("customerId");
            if (!(id.isTextual() || id.isNumber()) || id.asText().isBlank()) {
                throw ApiException.notFound(EProjectError.PRJ_026);
            }
            return Long.valueOf(id.asText());
        });
    }
}
