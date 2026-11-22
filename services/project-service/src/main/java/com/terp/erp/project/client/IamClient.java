package com.terp.erp.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.config.ProjectProperties;

import java.util.Objects;

/**
 * IAM as the caller: a project team member must be a user of the project's company. GET /api/v1/iam/users/{id}
 * requires IAM_USER_VIEW, so a project manager without it gets IAM's 403 relayed (COMMON_004).
 */
@Component
public class IamClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IamUser(String id, String companyId, String username, String fullName, String status) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public IamClient(ProjectProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.iamServiceUrl(), mapper, EProjectError.PRJ_005, "iam-service");
        this.mapper = mapper;
    }

    /** The user when it belongs to companyId; IAM's 404 (or another company) answers PRJ_008 (400). */
    public IamUser requireCompanyUser(Long userId, Long companyId) {
        IamUser u;
        try {
            JsonNode data = http.call(c -> c.get().uri("/api/v1/iam/users/{id}", userId)).path("data");
            u = mapper.convertValue(data, IamUser.class);
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EProjectError.PRJ_008, userId);
            }
            throw e;
        }
        if (u == null || !Objects.equals(u.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EProjectError.PRJ_008, userId);
        }
        return u;
    }
}
