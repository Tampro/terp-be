package com.terp.erp.recruitment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.config.RecruitmentProperties;

/**
 * Employees of employee-service read as the caller (their token): EMPLOYEE_VIEW and the company check apply there, so
 * another company's employee answers EMP_001 (relayed).
 */
@Component
public class EmployeeClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Employee(String id, String companyId, String fullName, String email, Ref designation) {
        public String designationName() {
            return designation == null ? null : designation.name();
        }
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public EmployeeClient(RecruitmentProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.employeeServiceUrl(), mapper, ERecruitmentError.REC_012, "employee-service");
        this.mapper = mapper;
    }

    public Employee employee(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/employees/{id}", id)).path("data");
        return mapper.convertValue(data, Employee.class);
    }
}
