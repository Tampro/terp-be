package com.terp.erp.report.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Call;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.config.ReportProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Company Dashboard (docs/analysis/06 §5.1). Every card comes from the service that owns the data, called with the
 * caller's token: a card the caller may not see (or whose service is down) is null with the reason, the rest still
 * shows. No number is computed or cached here.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    static final int RECENT = 5;
    static final int UPCOMING_DAYS = 30;

    private final Fanout fanout;
    private final ReportProperties urls;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Schema(description = "Each part: data, or data = null with error = the reason (COMMON_004 = no permission, "
            + "UNAVAILABLE = the service did not answer)")
    public record CompanyDashboard(LocalDate today, int year,
                                   @Schema(description = "IAM: company name and plan limits") Part company,
                                   @Schema(description = "Total employees, branches, birthdays today, hiring trend")
                                   Part employees,
                                   @Schema(description = "Present today, attendance rate, last 7 days") Part attendance,
                                   @Schema(description = "Pending leaves, today's leave, this month's overview")
                                   Part leave,
                                   @Schema(description = "Latest leave applications") Part recentLeaves,
                                   @Schema(description = "Payroll this month and the net pay trend") Part payroll,
                                   @Schema(description = "Active jobs, career page slug, candidate pipeline")
                                   Part recruitment,
                                   @Schema(description = "Latest candidates") Part recentCandidates,
                                   @Schema(description = "Latest announcements") Part announcements,
                                   @Schema(description = "Meetings from today on (not cancelled)") Part upcomingMeetings,
                                   @Schema(description = "Asset status distribution and more") Part assets) {
    }

    public CompanyDashboard dashboard(Long companyId, Integer requestedYear) {
        LocalDate today = LocalDate.now(clock);
        int year = requestedYear == null ? today.getYear() : requestedYear;
        String c = companyId == null ? "" : "companyId=" + companyId;
        String q = c.isEmpty() ? "" : "?" + c;
        String and = c.isEmpty() ? "" : "&" + c;
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("company", new Call(urls.iamServiceUrl(), "/api/v1/me/company" + q));
        calls.put("employees", new Call(urls.employeeServiceUrl(), "/api/v1/employees/dashboard?year=" + year + and));
        calls.put("attendance", new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/dashboard" + q));
        calls.put("leave", new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/leave/dashboard" + q));
        calls.put("recentLeaves", new Call(urls.attendanceServiceUrl(),
                "/api/v1/attendance/leave/applications?size=" + RECENT + "&sort=createdDate,desc" + and));
        calls.put("payroll", new Call(urls.payrollServiceUrl(), "/api/v1/payroll/dashboard?year=" + year + and));
        calls.put("recruitment", new Call(urls.recruitmentServiceUrl(), "/api/v1/recruitment/dashboard" + q));
        calls.put("recentCandidates", new Call(urls.recruitmentServiceUrl(),
                "/api/v1/recruitment/candidates?size=" + RECENT + "&sort=createdDate,desc" + and));
        calls.put("announcements", new Call(urls.employeeServiceUrl(),
                "/api/v1/org/announcements?size=" + RECENT + "&sort=createdDate,desc" + and));
        calls.put("upcomingMeetings", new Call(urls.meetingServiceUrl(), "/api/v1/meeting/meetings/range?from="
                + today + "&to=" + today.plusDays(UPCOMING_DAYS) + and));
        calls.put("assets", new Call(urls.assetServiceUrl(), "/api/v1/asset/dashboard" + q));
        Map<String, Part> p = fanout.all(calls);
        return new CompanyDashboard(today, year, p.get("company"), p.get("employees"), p.get("attendance"),
                p.get("leave"), list(p.get("recentLeaves")), p.get("payroll"), p.get("recruitment"),
                list(p.get("recentCandidates")), list(p.get("announcements")), upcoming(p.get("upcomingMeetings")),
                p.get("assets"));
    }

    /** A paged list (ResponseListDto) shown as its rows only. */
    Part list(Part part) {
        if (part == null || part.data() == null) {
            return part;
        }
        JsonNode rows = part.data().has("content") ? part.data().get("content") : part.data();
        return new Part(rows, null);
    }

    /** The first meetings that still happen, already ordered by date and time by meeting-service. */
    Part upcoming(Part part) {
        if (part == null || part.data() == null || !part.data().isArray()) {
            return part;
        }
        ArrayNode out = mapper.createArrayNode();
        for (JsonNode m : part.data()) {
            if (out.size() == RECENT) {
                break;
            }
            if (!"CANCELLED".equals(m.path("status").asText())) {
                out.add(m);
            }
        }
        return new Part(out, null);
    }
}
