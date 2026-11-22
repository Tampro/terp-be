package com.terp.erp.report.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.terp.erp.exception.ApiException;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Call;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.common.EReportError;
import com.terp.erp.report.config.ReportProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified calendar (docs/analysis/06 §5.2, ERP manual §11.3, FR-COL-CAL-001): every dated thing of a period in one
 * list. Each kind comes from its own service with the caller's token; a kind the caller may not see is left out and
 * named in `unavailable` with the reason. `kinds` limits the calls to the kinds the FE filter shows.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {
    static final int MAX_DAYS = 62;
    static final int UPCOMING = 10;
    /** Paged sources are read in one page; a month of contracts or interviews stays far below this. */
    static final int PAGE = 1000;

    private final Fanout fanout;
    private final ReportProperties urls;
    private final Clock clock;

    public enum EKind {
        MEETING, HOLIDAY, LEAVE, BIRTHDAY, SALES_INVOICE, PURCHASE_INVOICE, EVENT, DEAL_TASK, LEAD_TASK,
        CONTRACT_END, PROJECT_TASK, ZOOM_MEETING, INTERVIEW
    }

    @Schema(description = "One entry. Timed kinds (meetings, Zoom, interviews, tasks with a time) have times; the "
            + "others are all-day (start..end inclusive).")
    public record Event(EKind kind, String id, String title, LocalDate start, LocalDate end, LocalTime startTime,
                        LocalTime endTime, boolean allDay, String status) {
    }

    public record CalendarView(LocalDate from, LocalDate to, LocalDate today, List<Event> events,
                               @Schema(description = "From today on, in date order, at most 10") List<Event> upcoming,
                               @Schema(description = "Kinds left out, with the reason (COMMON_004 = no permission)")
                               Map<EKind, String> unavailable,
                               @Schema(description = "Events per kind in the period (This Month panel); kinds left "
                                       + "out are absent") Map<EKind, Long> counts,
                               long total) {
    }

    public CalendarView calendar(Long companyId, LocalDate from, LocalDate to) {
        return calendar(companyId, from, to, null);
    }

    /** kinds null or empty = every kind. */
    public CalendarView calendar(Long companyId, LocalDate from, LocalDate to, Collection<EKind> kinds) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest(EReportError.REP_002);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EReportError.REP_001, MAX_DAYS);
        }
        Set<EKind> wanted = kinds == null || kinds.isEmpty() ? EnumSet.allOf(EKind.class) : EnumSet.copyOf(kinds);
        String range = "from=" + from + "&to=" + to + (companyId == null ? "" : "&companyId=" + companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        for (EKind kind : wanted) {
            calls.put(kind.name(), call(kind, range, from, to, companyId));
        }
        Map<String, Part> parts = fanout.all(calls);

        List<Event> events = new ArrayList<>();
        Map<EKind, String> unavailable = new LinkedHashMap<>();
        for (EKind kind : wanted) {
            Part p = parts.get(kind.name());
            if (p == null || !p.ok()) {
                unavailable.put(kind, p == null ? Fanout.UNAVAILABLE : p.error());
            } else if (p.data() != null) {
                events.addAll(events(kind, p.data()));
            }
        }
        events.sort(Comparator.comparing(Event::start)
                .thenComparing(e -> e.startTime() == null ? LocalTime.MIN : e.startTime())
                .thenComparing(Event::kind));
        LocalDate today = LocalDate.now(clock);
        List<Event> upcoming = events.stream().filter(e -> !e.end().isBefore(today)).limit(UPCOMING).toList();
        Map<EKind, Long> counts = new LinkedHashMap<>();
        for (EKind kind : wanted) {
            if (!unavailable.containsKey(kind)) {
                counts.put(kind, events.stream().filter(e -> e.kind() == kind).count());
            }
        }
        return new CalendarView(from, to, today, events, upcoming, unavailable, counts, events.size());
    }

    private Call call(EKind kind, String range, LocalDate from, LocalDate to, Long companyId) {
        String company = companyId == null ? "" : "&companyId=" + companyId;
        return switch (kind) {
            case MEETING -> new Call(urls.meetingServiceUrl(), "/api/v1/meeting/meetings/range?" + range);
            case HOLIDAY -> new Call(urls.employeeServiceUrl(), "/api/v1/org/holidays/calendar?" + range);
            case LEAVE -> new Call(urls.attendanceServiceUrl(), "/api/v1/attendance/leave/applications/calendar?" + range);
            case BIRTHDAY -> new Call(urls.employeeServiceUrl(), "/api/v1/employees/birthdays?" + range);
            case SALES_INVOICE -> new Call(urls.commerceServiceUrl(), "/api/v1/sales/invoices/calendar?" + range);
            case PURCHASE_INVOICE -> new Call(urls.commerceServiceUrl(), "/api/v1/purchase/invoices/calendar?" + range);
            case EVENT -> new Call(urls.meetingServiceUrl(), "/api/v1/meeting/events/calendar?" + range);
            case DEAL_TASK -> new Call(urls.crmServiceUrl(), "/api/v1/crm/tasks/calendar?subject=DEAL&" + range);
            case LEAD_TASK -> new Call(urls.crmServiceUrl(), "/api/v1/crm/tasks/calendar?subject=LEAD&" + range);
            case CONTRACT_END -> new Call(urls.crmServiceUrl(), "/api/v1/crm/contracts?endFrom=" + from + "&endTo=" + to
                    + company + "&size=" + PAGE + "&sort=endDate");
            case PROJECT_TASK -> new Call(urls.projectServiceUrl(), "/api/v1/projects/tasks/calendar?" + range);
            case ZOOM_MEETING -> new Call(urls.meetingServiceUrl(), "/api/v1/meeting/zoom/meetings/calendar?" + range);
            case INTERVIEW -> new Call(urls.recruitmentServiceUrl(), "/api/v1/recruitment/interviews?" + range
                    + "&size=" + PAGE + "&sort=interviewDate&sort=startTime");
        };
    }

    public static List<Event> events(EKind kind, JsonNode data) {
        return switch (kind) {
            case MEETING -> meetings(data);
            case HOLIDAY -> holidays(data);
            case LEAVE -> leaves(data);
            case BIRTHDAY -> birthdays(data);
            case SALES_INVOICE -> invoices(EKind.SALES_INVOICE, data, "customerName");
            case PURCHASE_INVOICE -> invoices(EKind.PURCHASE_INVOICE, data, "vendorName");
            case EVENT -> companyEvents(data);
            case DEAL_TASK -> crmTasks(EKind.DEAL_TASK, data);
            case LEAD_TASK -> crmTasks(EKind.LEAD_TASK, data);
            case CONTRACT_END -> contractEnds(data);
            case PROJECT_TASK -> projectTasks(data);
            case ZOOM_MEETING -> zoomMeetings(data);
            case INTERVIEW -> interviews(data);
        };
    }

    static List<Event> meetings(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode m : list) {
            if ("CANCELLED".equals(m.path("status").asText())) {
                continue;
            }
            LocalDate d = date(m, "meetingDate");
            out.add(new Event(EKind.MEETING, m.path("id").asText(), m.path("title").asText(), d, d,
                    time(m, "startTime"), time(m, "endTime"), false, m.path("status").asText(null)));
        }
        return out;
    }

    static List<Event> holidays(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode h : list) {
            out.add(new Event(EKind.HOLIDAY, h.path("id").asText(), h.path("name").asText(), date(h, "startDate"),
                    date(h, "endDate"), null, null, true, null));
        }
        return out;
    }

    /** TODO(assumption): only approved leaves are shown (a pending request is not an absence yet). */
    public static List<Event> leaves(JsonNode calendar) {
        List<Event> out = new ArrayList<>();
        for (JsonNode row : calendar.path("rows")) {
            String name = row.path("employee").path("fullName").asText();
            for (JsonNode l : row.path("leaves")) {
                if (!"APPROVED".equals(l.path("status").asText())) {
                    continue;
                }
                String type = l.path("leaveType").path("name").asText("");
                out.add(new Event(EKind.LEAVE, l.path("id").asText(), type.isEmpty() ? name : name + " – " + type,
                        date(l, "startDate"), date(l, "endDate"), null, null, true, l.path("status").asText()));
            }
        }
        return out;
    }

    static List<Event> birthdays(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode b : list) {
            LocalDate d = date(b, "date");
            out.add(new Event(EKind.BIRTHDAY, b.path("employeeId").asText(), b.path("fullName").asText(), d, d,
                    null, null, true, null));
        }
        return out;
    }

    /**
     * TODO(assumption): invoices sit on their due date (when they must be paid or are collected), drafts included
     * with their status; the source answers by due date.
     */
    static List<Event> invoices(EKind kind, JsonNode list, String partyField) {
        List<Event> out = new ArrayList<>();
        for (JsonNode i : list) {
            LocalDate d = date(i, "dueDate");
            out.add(new Event(kind, i.path("id").asText(), join(i.path("number").asText(), i.path(partyField).asText("")),
                    d, d, null, null, true, i.path("status").asText(null)));
        }
        return out;
    }

    /** Company events: the source already returns approved events only. */
    static List<Event> companyEvents(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode e : list) {
            LocalTime start = time(e, "startTime");
            out.add(new Event(EKind.EVENT, e.path("id").asText(), e.path("title").asText(), date(e, "startDate"),
                    date(e, "endDate"), start, time(e, "endTime"), start == null, e.path("status").asText(null)));
        }
        return out;
    }

    /** Lead / deal tasks, done ones included (status DONE) so the day shows what was planned. */
    static List<Event> crmTasks(EKind kind, JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode t : list) {
            LocalDate d = date(t, "date");
            LocalTime at = time(t, "time");
            out.add(new Event(kind, t.path("id").asText(), join(t.path("name").asText(),
                    t.path("subjectName").asText("")), d, d, at, null, at == null, t.path("status").asText(null)));
        }
        return out;
    }

    /** TODO(assumption): every contract ending in the period is shown, declined and closed ones too (see status). */
    static List<Event> contractEnds(JsonNode page) {
        List<Event> out = new ArrayList<>();
        for (JsonNode c : page.path("content")) {
            if (c.path("endDate").asText("").isEmpty()) {
                continue;
            }
            LocalDate d = date(c, "endDate");
            String title = join(c.path("number").asText(), c.path("subject").asText(""));
            out.add(new Event(EKind.CONTRACT_END, c.path("id").asText(), join(title, c.path("customerName").asText("")),
                    d, d, null, null, true, c.path("status").asText(null)));
        }
        return out;
    }

    /**
     * Project tasks sit on their due date ("project task due"); a task without one is not on the calendar.
     * status = DONE, otherwise the stage name.
     */
    static List<Event> projectTasks(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode t : list) {
            if (t.path("dueDate").asText("").isEmpty()) {
                continue;
            }
            LocalDate d = date(t, "dueDate");
            String status = t.path("done").asBoolean(false) ? "DONE" : t.path("stageName").asText(null);
            out.add(new Event(EKind.PROJECT_TASK, t.path("id").asText(), join(t.path("projectName").asText(""),
                    t.path("title").asText()), d, d, null, null, true, status));
        }
        return out;
    }

    static List<Event> zoomMeetings(JsonNode list) {
        List<Event> out = new ArrayList<>();
        for (JsonNode z : list) {
            if ("CANCELLED".equals(z.path("status").asText())) {
                continue;
            }
            LocalDate d = date(z, "startDate");
            out.add(new Event(EKind.ZOOM_MEETING, z.path("id").asText(), z.path("topic").asText(), d, d,
                    time(z, "startTime"), time(z, "endTime"), false, z.path("status").asText(null)));
        }
        return out;
    }

    /** Interviews (a page); cancelled ones are left out like cancelled meetings. */
    static List<Event> interviews(JsonNode page) {
        List<Event> out = new ArrayList<>();
        for (JsonNode i : page.path("content")) {
            if ("CANCELLED".equals(i.path("status").asText())) {
                continue;
            }
            LocalDate d = date(i, "date");
            LocalTime start = time(i, "startTime");
            int minutes = i.path("durationMinutes").asInt(0);
            LocalTime end = start == null || minutes <= 0 ? null : start.plusMinutes(minutes);
            String title = join(i.path("candidate").path("fullName").asText(), i.path("round").path("name").asText(""));
            out.add(new Event(EKind.INTERVIEW, i.path("id").asText(), title, d, d, start, end, start == null,
                    i.path("status").asText(null)));
        }
        return out;
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        return b == null || b.isEmpty() ? a : a + " – " + b;
    }

    private static LocalDate date(JsonNode n, String field) {
        return LocalDate.parse(n.path(field).asText());
    }

    private static LocalTime time(JsonNode n, String field) {
        String t = n.path(field).asText("");
        return t.isEmpty() ? null : LocalTime.parse(t);
    }
}
