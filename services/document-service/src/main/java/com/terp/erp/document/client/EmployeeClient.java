package com.terp.erp.document.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.config.DocumentProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employees of employee-service read as the caller (their token): EMPLOYEE_VIEW and the company check apply there, so
 * another company's employee answers EMP_001 (relayed).
 */
@Component
public class EmployeeClient {
    /** At most this many ids per list call (employee-service's limit). */
    static final int MAX_IDS = 100;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** userId = the IAM login account (null when the employee has none). */
    public record Employee(String id, String companyId, String employeeNo, String fullName, String email,
                           String profileImageUrl, String status, Ref department, Ref designation, String userId) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public EmployeeClient(DocumentProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.employeeServiceUrl(), mapper, EDocumentError.DOC_006, "employee-service");
        this.mapper = mapper;
    }

    public Employee employee(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/employees/{id}", id)).path("data");
        return mapper.convertValue(data, Employee.class);
    }

    /** Employees of the caller's company by id, in chunks of {@value #MAX_IDS}; unknown ids are simply absent. */
    public Map<Long, Employee> byIds(Collection<Long> ids, Long companyId) {
        List<Long> all = ids.stream().distinct().toList();
        Map<Long, Employee> out = new java.util.HashMap<>();
        for (int i = 0; i < all.size(); i += MAX_IDS) {
            List<Long> chunk = all.subList(i, Math.min(all.size(), i + MAX_IDS));
            String csv = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            JsonNode content = http.call(c -> c.get().uri(u -> u.path("/api/v1/employees")
                    .queryParam("ids", csv).queryParam("companyId", companyId)
                    .queryParam("size", chunk.size()).build())).path("data").path("content");
            List<Employee> list = mapper.convertValue(content, new TypeReference<>() {
            });
            out.putAll(list.stream().collect(Collectors.toMap(e -> Long.valueOf(e.id()), Function.identity())));
        }
        return out;
    }
}
