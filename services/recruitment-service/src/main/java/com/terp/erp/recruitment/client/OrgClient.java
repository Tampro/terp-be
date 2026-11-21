package com.terp.erp.recruitment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.config.RecruitmentProperties;

/**
 * Branches and departments of employee-service, read as the caller (their token): its ORG_VIEW and company checks
 * apply, and another company's branch answers EMP_002 / department EMP_003 (relayed).
 */
@Component
public class OrgClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Branch(String id, String companyId, String name, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Department(String id, String companyId, String name, Ref branch, String status) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public OrgClient(RecruitmentProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.employeeServiceUrl(), mapper, ERecruitmentError.REC_012, "employee-service");
        this.mapper = mapper;
    }

    public Branch branch(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/org/branches/{id}", id)).path("data");
        return mapper.convertValue(data, Branch.class);
    }

    public Department department(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/org/departments/{id}", id)).path("data");
        return mapper.convertValue(data, Department.class);
    }

    /** For display: a deleted or unreachable branch/department shows no name instead of failing the page. */
    public String branchName(Long id) {
        try {
            return id == null ? null : branch(id).name();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String departmentName(Long id) {
        try {
            return id == null ? null : department(id).name();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
