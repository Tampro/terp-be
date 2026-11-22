package com.terp.erp.support.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.config.SupportProperties;

/**
 * IAM as the caller: a messenger peer must be an ACTIVE user of the caller's company. GET /api/v1/iam/colleagues/{id}
 * (MESSENGER_USE) answers only for such a user and 404 for anyone else, so the company check is IAM's.
 */
@Component
public class IamClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IamUser(String id, String username, String fullName) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public IamClient(SupportProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.iamServiceUrl(), mapper, ESupportError.SUP_003, "iam-service");
        this.mapper = mapper;
    }

    /** The colleague; IAM's 404 (unknown, other company, locked) answers SUP_029 (400). */
    public IamUser requireActiveCompanyUser(Long userId, Long companyId) {
        try {
            JsonNode data = http.call(c -> c.get().uri("/api/v1/iam/colleagues/{id}", userId)).path("data");
            IamUser u = mapper.convertValue(data, IamUser.class);
            if (u == null) {
                throw ApiException.badRequest(ESupportError.SUP_029, userId);
            }
            return u;
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(ESupportError.SUP_029, userId);
            }
            throw e;
        }
    }
}
