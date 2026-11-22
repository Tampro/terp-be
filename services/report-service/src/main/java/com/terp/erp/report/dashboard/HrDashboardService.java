package com.terp.erp.report.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Call;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.config.ReportProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HR dashboard and the employee's own dashboard (FR-ERP-DSH-001, manual §15). Like the company dashboard, every part
 * is read from the service that owns it with the caller's token and degrades on its own: a part the caller may not
 * see, whose service failed, or that no service offers yet is data = null and is listed in {@code unavailable} with
 * the reason (the source's error code, UNAVAILABLE, or {@value #NOT_AVAILABLE} when there is no source endpoint).
 */
@Service
@RequiredArgsConstructor
public class HrDashboardService {
    /** No service offers this part yet. */
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    static final int RECENT = 5;
    static final int UPCOMING_DAYS = 30;
    /** Branch list page size: a company with more branches shows the first ones only. */
    static final int BRANCHES = 200;

    /**
     * "My" parts without a self endpoint in their service yet (checked 2026-11-22): NOT_AVAILABLE until they add one.
     */
    static final List<String> MY_GAPS = List.of("myAttendanceThisMonth", "myLeaveBalances", "myLeaveApplications",
            "myAwards", "myComplaints");

    private final Fanout fanout;
    private final ReportProperties urls;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Schema(description = "HR dashboard. Each part: data, or data = null with error = the reason; failed parts are also "
            + "listed in unavailable")
    public record HrDashboard(LocalDate today,
                              @Schema(description = "presentToday, absentToday, onLeaveToday, activeEmployees, "
                                      + "attendanceRateToday (% present of active employees; null when unknown)")
                              Part attendanceToday,
                              @Schema(description = "count = employees with no attendance record today")
                              Part missingAttendanceToday,
                              @Schema(description = "[{branchId, name, employees}]") Part employeesByBranch,
                              @Schema(description = "[{departmentId, name, employees}]") Part employeesByDepartment,
                              @Schema(description = "Latest promotions") Part recentPromotions,
                              @Schema(description = "Latest terminations") Part recentTerminations,
                              @Schema(description = "Approved company events in the next 30 days") Part upcomingEvents,
                              @Schema(description = "Latest leave applications") Part recentLeaves,
                              @Schema(description = "Latest ACTIVE announcements") Part activeAnnouncements,
                              @Schema(description = "part → reason for every part without data")
                              Map<String, String> unavailable) {
    }

    @Schema(description = "My dashboard. Parts without a self endpoint in their service are NOT_AVAILABLE")
    public record MyDashboard(LocalDate today, Map<String, Part> parts, Map<String, String> unavailable) {
    }

    public HrDashboard hrDashboard(Long companyId) {
        LocalDate today = LocalDate.now(clock);
        String c = companyId == null ? "" : "companyId=" + companyId;
        String q = c.isEmpty() ? "" : "?" + c;
        String and = c.isEmpty() ? "" : "&" + c;
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("attendance", new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/dashboard" + q));
        calls.put("branches", new Call(urls.employeeServiceUrl(), "/api/v1/org/branches?size=" + BRANCHES
                + "&sort=name" + and));
        calls.put("employees", new Call(urls.employeeServiceUrl(), "/api/v1/employees/dashboard?year="
                + today.getYear() + and));
        calls.put("promotions", new Call(urls.employeeServiceUrl(), "/api/v1/lifecycle/promotions?size=" + RECENT
                + "&sort=promotionDate,desc" + and));
        calls.put("terminations", new Call(urls.employeeServiceUrl(), "/api/v1/lifecycle/terminations?size=" + RECENT
                + "&sort=terminationDate,desc" + and));
        calls.put("events", new Call(urls.meetingServiceUrl(), "/api/v1/meeting/events/calendar?from=" + today
                + "&to=" + today.plusDays(UPCOMING_DAYS) + and));
        calls.put("leaves", new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/leave/applications?size="
                + RECENT + "&sort=createdDate,desc" + and));
        calls.put("announcements", new Call(urls.employeeServiceUrl(), "/api/v1/org/announcements?status=ACTIVE&size="
                + RECENT + "&sort=startDate,desc" + and));
        Map<String, Part> p = fanout.all(calls);

        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("attendanceToday", attendanceToday(p.get("attendance")));
        parts.put("missingAttendanceToday", missingToday(p.get("attendance")));
        parts.put("employeesByBranch", byBranch(p.get("branches")));
        parts.put("employeesByDepartment", byDepartment(p.get("employees")));
        parts.put("recentPromotions", rows(p.get("promotions")));
        parts.put("recentTerminations", rows(p.get("terminations")));
        parts.put("upcomingEvents", first(p.get("events")));
        parts.put("recentLeaves", rows(p.get("leaves")));
        parts.put("activeAnnouncements", rows(p.get("announcements")));
        return new HrDashboard(today, parts.get("attendanceToday"), parts.get("missingAttendanceToday"),
                parts.get("employeesByBranch"), parts.get("employeesByDepartment"), parts.get("recentPromotions"),
                parts.get("recentTerminations"), parts.get("upcomingEvents"), parts.get("recentLeaves"),
                parts.get("activeAnnouncements"), unavailable(parts));
    }

    /** The signed-in employee's own dashboard, from the self endpoints of the owning services. */
    public MyDashboard myDashboard() {
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("profile", new Call(urls.employeeServiceUrl(), "/api/v1/employees/me"));
        calls.put("today", new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/me/today"));
        calls.put("warnings", new Call(urls.employeeServiceUrl(), "/api/v1/lifecycle/warnings/my?size=" + RECENT
                + "&sort=warningDate,desc"));
        calls.put("announcements", new Call(urls.employeeServiceUrl(), "/api/v1/org/announcements/my?size=" + RECENT
                + "&sort=startDate,desc"));
        Map<String, Part> p = fanout.all(calls);
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("myProfile", profile(p.get("profile")));
        parts.put("myShift", field(p.get("today"), "shift"));
        parts.put("myAttendanceToday", attendanceTodayOfMine(p.get("today")));
        parts.put("myWarnings", rows(p.get("warnings")));
        parts.put("myAnnouncements", rows(p.get("announcements")));
        for (String name : MY_GAPS) {
            parts.put(name, new Part(null, NOT_AVAILABLE));
        }
        return new MyDashboard(LocalDate.now(clock), parts, unavailable(parts));
    }

    /** EmployeeDetail of /employees/me, reduced to the card: no banking, address or documents on a dashboard. */
    Part profile(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        JsonNode d = part.data();
        ObjectNode out = mapper.createObjectNode();
        out.put("employeeId", d.path("id").asText(null));
        out.put("employeeNo", d.path("employeeNo").asText(null));
        out.put("fullName", d.path("fullName").asText(null));
        out.put("profileImageUrl", d.path("profileImageUrl").asText(null));
        for (String ref : List.of("branch", "department", "designation")) {
            JsonNode r = d.get(ref);
            out.set(ref, r == null ? mapper.nullNode() : r);
        }
        return new Part(out, null);
    }

    /** One field of an object part; an absent field (e.g. no shift assigned) is data = null without an error. */
    Part field(Part part, String name) {
        if (failed(part)) {
            return missing(part);
        }
        JsonNode v = part.data().get(name);
        return new Part(v == null || v.isNull() ? mapper.nullNode() : v, null);
    }

    /** /attendance/me/today without the shift (its own part): {date, record, canClockIn, canClockOut}. */
    Part attendanceTodayOfMine(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        ObjectNode out = part.data().deepCopy();
        out.remove("shift");
        return new Part(out, null);
    }

    static Map<String, String> unavailable(Map<String, Part> parts) {
        Map<String, String> out = new LinkedHashMap<>();
        parts.forEach((k, v) -> {
            if (v == null) {
                out.put(k, Fanout.UNAVAILABLE);
            } else if (v.data() == null) {
                out.put(k, v.error() == null ? Fanout.UNAVAILABLE : v.error());
            }
        });
        return out;
    }

    /** Today's figures of the attendance dashboard, with the rate of today (present / active employees). */
    Part attendanceToday(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        JsonNode d = part.data();
        ObjectNode out = mapper.createObjectNode();
        long present = d.path("presentToday").asLong(0);
        out.put("today", d.path("today").asText(null));
        out.put("presentToday", present);
        out.put("absentToday", d.path("absentToday").asLong(0));
        out.put("onLeaveToday", d.path("onLeaveToday").asLong(0));
        JsonNode active = d.get("activeEmployees");
        if (active == null || active.isNull()) {
            out.putNull("activeEmployees");
            out.putNull("attendanceRateToday");
        } else {
            out.put("activeEmployees", active.asLong());
            if (active.asLong() > 0) {
                out.put("attendanceRateToday", BigDecimal.valueOf(present * 100)
                        .divide(BigDecimal.valueOf(active.asLong()), 2, RoundingMode.HALF_UP));
            } else {
                out.putNull("attendanceRateToday");
            }
        }
        return new Part(out, null);
    }

    /** notMarkedToday of the attendance dashboard (null there = headcount unknown = this part unavailable). */
    Part missingToday(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        JsonNode n = part.data().get("notMarkedToday");
        if (n == null || n.isNull()) {
            return new Part(null, Fanout.UNAVAILABLE);
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("count", n.asLong());
        return new Part(out, null);
    }

    /** Branch rows (employeeCount) → [{branchId, name, employees}]. */
    Part byBranch(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        ArrayNode out = mapper.createArrayNode();
        for (JsonNode b : content(part.data())) {
            ObjectNode o = out.addObject();
            o.put("branchId", b.path("id").asText(null));
            o.put("name", b.path("name").asText(null));
            o.put("employees", b.path("employeeCount").asLong(0));
        }
        return new Part(out, null);
    }

    /** byDepartment of the employee dashboard. */
    Part byDepartment(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        JsonNode rows = part.data().get("byDepartment");
        return rows == null || !rows.isArray() ? new Part(null, Fanout.UNAVAILABLE) : new Part(rows, null);
    }

    /** A paged list (ResponseListDto) shown as its rows only. */
    Part rows(Part part) {
        return failed(part) ? missing(part) : new Part(content(part.data()), null);
    }

    /** The first {@value #RECENT} entries of a plain list. */
    Part first(Part part) {
        if (failed(part)) {
            return missing(part);
        }
        ArrayNode out = mapper.createArrayNode();
        for (JsonNode e : content(part.data())) {
            if (out.size() == RECENT) {
                break;
            }
            out.add(e);
        }
        return new Part(out, null);
    }

    private static Part missing(Part part) {
        return part == null ? new Part(null, Fanout.UNAVAILABLE) : part;
    }

    private static boolean failed(Part part) {
        return part == null || part.data() == null;
    }

    private static JsonNode content(JsonNode data) {
        return data.has("content") ? data.get("content") : data;
    }
}
