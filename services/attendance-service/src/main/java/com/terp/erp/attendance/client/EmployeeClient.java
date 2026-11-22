package com.terp.erp.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.config.AttendanceProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * employee-service, called as the caller (their token): its own EMPLOYEE_VIEW / ORG_VIEW and company checks apply,
 * so attendance never sees employees the caller could not see there.
 */
@Component
public class EmployeeClient {
    /** Largest page employee-service serves; a company has at most a few thousand employees. */
    private static final int PAGE = 200;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** companyId is filled by the detail endpoint only. */
    public record Employee(String id, String companyId, String employeeNo, String employeeCode, String fullName,
                           String email,
                           String profileImageUrl, Ref branch, Ref designation, String status, String shiftId,
                           String attendancePolicyId) {
        public Long branchIdOrNull() {
            return branch == null || branch.id() == null ? null : Long.valueOf(branch.id());
        }
    }

    public record EmployeePage(List<Employee> content, long totalElements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Holiday(String id, String name, LocalDate startDate, LocalDate endDate, boolean allBranches,
                          List<Ref> branches) {
        public boolean appliesTo(Long branchId) {
            return allBranches || (branchId != null && branches != null
                    && branches.stream().anyMatch(b -> String.valueOf(branchId).equals(b.id())));
        }
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public EmployeeClient(AttendanceProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.employeeServiceUrl(), mapper, EAttendanceError.ATT_012, "employee-service");
        this.mapper = mapper;
    }

    /** One page of the company's employees, by employee number (the grid rows). */
    public EmployeePage page(Long companyId, int page, int size) {
        return page(companyId, null, page, size);
    }

    /** keyword = employee-service's own search (name, email, number, code). */
    public EmployeePage page(Long companyId, String keyword, int page, int size) {
        JsonNode data = http.call(c -> c.get().uri(u -> u.path("/api/v1/employees")
                .queryParamIfPresent("companyId", java.util.Optional.ofNullable(companyId))
                .queryParamIfPresent("keyword", java.util.Optional.ofNullable(keyword).filter(k -> !k.isBlank()))
                .queryParam("page", page).queryParam("size", size).queryParam("sort", "employeeNo,asc").build()))
                .path("data");
        List<Employee> list = new ArrayList<>();
        data.path("content").forEach(n -> list.add(mapper.convertValue(n, Employee.class)));
        return new EmployeePage(list, data.path("totalElements").asLong());
    }

    /** Every employee of the company (import: code/email → id). */
    public List<Employee> all(Long companyId) {
        List<Employee> out = new ArrayList<>();
        for (int p = 0; ; p++) {
            EmployeePage page = page(companyId, p, PAGE);
            out.addAll(page.content());
            if (page.content().size() < PAGE || out.size() >= page.totalElements()) {
                return out;
            }
        }
    }

    /** One employee of the caller's company (EMP_001 relayed when it is not one). */
    public Employee get(Long employeeId) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/employees/{id}", employeeId)).path("data");
        return mapper.convertValue(data, Employee.class);
    }

    /**
     * The caller's own employee profile (linked by the IAM user id), same shape as the detail. Needs employee-service
     * {@code GET /api/v1/employees/me} for any authenticated company user: the list and detail need EMPLOYEE_VIEW,
     * which a staff member does not hold. Null when no profile is linked.
     */
    public Employee me() {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/employees/me")).path("data");
        return data.isMissingNode() || data.isNull() ? null : mapper.convertValue(data, Employee.class);
    }

    /** Holidays overlapping [from, to] of the company (employee-service holiday calendar, at most 400 days). */
    public List<Holiday> holidays(Long companyId, LocalDate from, LocalDate to) {
        JsonNode data = http.call(c -> c.get().uri(u -> u.path("/api/v1/org/holidays/calendar")
                .queryParamIfPresent("companyId", java.util.Optional.ofNullable(companyId))
                .queryParam("from", from).queryParam("to", to).build())).path("data");
        List<Holiday> list = new ArrayList<>();
        data.forEach(n -> list.add(mapper.convertValue(n, Holiday.class)));
        return list;
    }
}
