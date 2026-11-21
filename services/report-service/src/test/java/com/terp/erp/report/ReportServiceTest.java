package com.terp.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.report.calendar.CalendarService;
import com.terp.erp.report.calendar.CalendarService.EKind;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.common.EReportError;
import com.terp.erp.report.config.ReportProperties;
import com.terp.erp.report.dashboard.DashboardService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Fanout fanout = mock(Fanout.class);
    private final ReportProperties urls = new ReportProperties("i", "e", "a", "r", "p", "s", "m");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s.replace('\'', '"'));
    }

    @Test
    void calendarMergesTheKindsInDateOrderAndNamesWhatIsMissing() throws Exception {
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("MEETING", new Part(json("[{'id':'1','title':'Team','meetingDate':'2026-11-25','startTime':'09:00',"
                + "'endTime':'10:00','status':'SCHEDULED'},{'id':'2','title':'Gone','meetingDate':'2026-11-22',"
                + "'startTime':'09:00','endTime':'10:00','status':'CANCELLED'}]"), null));
        parts.put("HOLIDAY", new Part(json("[{'id':'7','name':'National Day','startDate':'2026-11-20','endDate':'2026-11-21'}]"), null));
        parts.put("LEAVE", new Part(null, "COMMON_004"));
        parts.put("BIRTHDAY", new Part(json("[{'employeeId':'9','fullName':'Test Person','date':'2026-11-23'}]"), null));
        when(fanout.all(any())).thenReturn(parts);

        var v = new CalendarService(fanout, urls, clock).calendar(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(v.events()).extracting(e -> e.kind() + ":" + e.title())
                .containsExactly("HOLIDAY:National Day", "BIRTHDAY:Test Person", "MEETING:Team");
        assertThat(v.unavailable()).containsEntry(EKind.LEAVE, "COMMON_004").hasSize(1);
        // This Month panel: per kind (the cancelled meeting is not counted), the unavailable kind left out
        assertThat(v.counts()).containsEntry(EKind.MEETING, 1L).containsEntry(EKind.HOLIDAY, 1L)
                .containsEntry(EKind.BIRTHDAY, 1L).doesNotContainKey(EKind.LEAVE);
        assertThat(v.total()).isEqualTo(3);
        // the holiday ends today, so it is still upcoming
        assertThat(v.upcoming()).hasSize(3);
    }

    @Test
    void onlyApprovedLeavesAreShown() throws Exception {
        var events = CalendarService.leaves(json("{'rows':[{'employee':{'fullName':'A Test'},'leaves':["
                + "{'id':'1','leaveType':{'name':'Sick'},'startDate':'2026-11-02','endDate':'2026-11-03','status':'APPROVED'},"
                + "{'id':'2','leaveType':{'name':'Annual'},'startDate':'2026-11-05','endDate':'2026-11-05','status':'PENDING'}]}]}"));
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.title()).isEqualTo("A Test – Sick");
            assertThat(e.end()).isEqualTo(LocalDate.of(2026, 11, 3));
        });
    }

    @Test
    void rangeIsLimited() {
        CalendarService s = new CalendarService(fanout, urls, clock);
        assertThatThrownBy(() -> s.calendar(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EReportError.REP_001);
        assertThatThrownBy(() -> s.calendar(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EReportError.REP_002);
    }

    @Test
    void dashboardKeepsFailedCardsAsNullWithTheReason() throws Exception {
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("payroll", new Part(null, "COMMON_004"));
        parts.put("recentLeaves", new Part(json("{'content':[{'id':'1'}],'totalElements':1}"), null));
        parts.put("upcomingMeetings", new Part(json("[{'id':'1','status':'CANCELLED'},{'id':'2','status':'SCHEDULED'}]"), null));
        when(fanout.all(any())).thenReturn(parts);

        var d = new DashboardService(fanout, urls, mapper, clock).dashboard(null, null);

        assertThat(d.year()).isEqualTo(2026);
        assertThat(d.payroll().data()).isNull();
        assertThat(d.payroll().error()).isEqualTo("COMMON_004");
        assertThat(d.recentLeaves().data().get(0).path("id").asText()).isEqualTo("1");
        assertThat(d.upcomingMeetings().data()).hasSize(1);
        assertThat(d.employees()).isNull();
    }
}
