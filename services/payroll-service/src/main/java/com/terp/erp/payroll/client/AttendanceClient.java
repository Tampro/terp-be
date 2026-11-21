package com.terp.erp.payroll.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.config.PayrollProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attendance of a pay period from attendance-service, read as the caller: ATTENDANCE_VIEW and the company check
 * apply there.
 */
@Component
public class AttendanceClient {
    /** attendance-service's limit per call. */
    static final int MAX_IDS = 100;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(LocalDate date, LocalTime clockIn, LocalTime clockOut, BigDecimal totalHours,
                      BigDecimal overtimeHours, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(String employeeId, int workingDays, BigDecimal presentDays, BigDecimal paidLeaveDays,
                          BigDecimal unpaidLeaveDays, BigDecimal lopDays, BigDecimal overtimeHours,
                          BigDecimal overtimeAmount, List<Day> records) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public AttendanceClient(PayrollProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.attendanceServiceUrl(), mapper, EPayrollError.PAY_011, "attendance-service");
        this.mapper = mapper;
    }

    /** By employee id; an employee attendance-service does not know in the company is absent from the map. */
    public Map<Long, Summary> summaries(Long companyId, LocalDate from, LocalDate to, Collection<Long> ids) {
        List<Long> all = ids.stream().distinct().toList();
        Map<Long, Summary> out = new HashMap<>();
        for (int i = 0; i < all.size(); i += MAX_IDS) {
            String csv = all.subList(i, Math.min(all.size(), i + MAX_IDS)).stream().map(String::valueOf)
                    .collect(Collectors.joining(","));
            JsonNode data = http.call(c -> c.get().uri(u -> u.path("/api/v1/attendance/payroll-summary")
                    .queryParam("companyId", companyId).queryParam("from", from).queryParam("to", to)
                    .queryParam("employeeIds", csv).build())).path("data");
            List<Summary> list = mapper.convertValue(data, new TypeReference<>() {
            });
            out.putAll(list.stream().collect(Collectors.toMap(s -> Long.valueOf(s.employeeId()), Function.identity())));
        }
        return out;
    }
}
