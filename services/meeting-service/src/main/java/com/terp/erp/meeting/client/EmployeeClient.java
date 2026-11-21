package com.terp.erp.meeting.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.config.MeetingProperties;

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
    public record Employee(String id, String companyId, String employeeNo, String fullName, String email,
                           String profileImageUrl, String status, Ref department, Ref designation) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public EmployeeClient(MeetingProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.employeeServiceUrl(), mapper, EMeetingError.MTG_006, "employee-service");
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Department(String id, String name, String branchId) {
    }

    /** Active departments of the company by id (employee-service; ORG_VIEW or EMPLOYEE_VIEW there). */
    public Map<Long, Department> activeDepartments(Long companyId) {
        JsonNode data = http.call(c -> c.get().uri(u -> u.path("/api/v1/org/departments/options")
                .queryParam("companyId", companyId).build())).path("data");
        List<Department> list = mapper.convertValue(data, new TypeReference<>() {
        });
        return list.stream().collect(Collectors.toMap(d -> Long.valueOf(d.id()), Function.identity(), (a, b) -> a));
    }
}
