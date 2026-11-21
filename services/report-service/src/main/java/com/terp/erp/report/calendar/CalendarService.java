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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calendar (docs/analysis/06 §5.2): meetings, holidays, leaves and birthdays of a period in one list. Each kind
 * comes from its own service with the caller's token; a kind the caller may not see is left out and named in
 * `unavailable` with the reason.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {
    static final int MAX_DAYS = 62;
    static final int UPCOMING = 10;

    private final Fanout fanout;
    private final ReportProperties urls;
    private final Clock clock;

    public enum EKind { MEETING, HOLIDAY, LEAVE, BIRTHDAY }

    @Schema(description = "One entry. Meetings have times; the others are all-day (start..end inclusive).")
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
        if (to.isBefore(from)) {
            throw ApiException.badRequest(EReportError.REP_002);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EReportError.REP_001, MAX_DAYS);
        }
        String range = "from=" + from + "&to=" + to + (companyId == null ? "" : "&companyId=" + companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put(EKind.MEETING.name(), new Call(urls.meetingServiceUrl(), "/api/v1/meeting/meetings/range?" + range));
        calls.put(EKind.HOLIDAY.name(), new Call(urls.employeeServiceUrl(), "/api/v1/org/holidays/calendar?" + range));
        calls.put(EKind.LEAVE.name(), new Call(urls.attendanceServiceUrl(),
                "/api/v1/attendance/leave/applications/calendar?" + range));
        calls.put(EKind.BIRTHDAY.name(), new Call(urls.employeeServiceUrl(), "/api/v1/employees/birthdays?" + range));
        Map<String, Part> parts = fanout.all(calls);

        List<Event> events = new ArrayList<>();
        Map<EKind, String> unavailable = new LinkedHashMap<>();
        for (EKind kind : EKind.values()) {
            Part p = parts.get(kind.name());
            if (p == null || !p.ok()) {
                unavailable.put(kind, p == null ? Fanout.UNAVAILABLE : p.error());
            } else if (p.data() != null) {
                events.addAll(switch (kind) {
                    case MEETING -> meetings(p.data());
                    case HOLIDAY -> holidays(p.data());
                    case LEAVE -> leaves(p.data());
                    case BIRTHDAY -> birthdays(p.data());
                });
            }
        }
        events.sort(Comparator.comparing(Event::start)
                .thenComparing(e -> e.startTime() == null ? LocalTime.MIN : e.startTime())
                .thenComparing(Event::kind));
        LocalDate today = LocalDate.now(clock);
        List<Event> upcoming = events.stream().filter(e -> !e.end().isBefore(today)).limit(UPCOMING).toList();
        Map<EKind, Long> counts = new LinkedHashMap<>();
        for (EKind kind : EKind.values()) {
            if (!unavailable.containsKey(kind)) {
                counts.put(kind, events.stream().filter(e -> e.kind() == kind).count());
            }
        }
        return new CalendarView(from, to, today, events, upcoming, unavailable, counts, events.size());
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

    private static LocalDate date(JsonNode n, String field) {
        return LocalDate.parse(n.path(field).asText());
    }

    private static LocalTime time(JsonNode n, String field) {
        String t = n.path(field).asText("");
        return t.isEmpty() ? null : LocalTime.parse(t);
    }
}
