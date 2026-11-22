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
    private final ReportProperties urls = new ReportProperties("i", "e", "a", "r", "p", "s", "m", "c", "crm", "prj");
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

        var v = new CalendarService(fanout, urls, clock).calendar(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                java.util.List.of(EKind.MEETING, EKind.HOLIDAY, EKind.LEAVE, EKind.BIRTHDAY));

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
    void kindsFilterCallsOnlyTheChosenServices() throws Exception {
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("SALES_INVOICE", new Part(json("[{'id':'3','number':'SI-2026-11-001','customerName':'Test Co',"
                + "'dueDate':'2026-11-24','status':'POSTED'}]"), null));
        parts.put("PROJECT_TASK", new Part(null, "COMMON_004"));
        org.mockito.ArgumentCaptor<Map<String, Fanout.Call>> calls = org.mockito.ArgumentCaptor.captor();
        when(fanout.all(calls.capture())).thenReturn(parts);

        var v = new CalendarService(fanout, urls, clock).calendar(7L, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), java.util.List.of(EKind.PROJECT_TASK, EKind.SALES_INVOICE));

        assertThat(calls.getValue()).containsOnlyKeys("SALES_INVOICE", "PROJECT_TASK");
        assertThat(calls.getValue().get("SALES_INVOICE")).isEqualTo(new Fanout.Call("c",
                "/api/v1/sales/invoices/calendar?from=2026-11-01&to=2026-11-30&companyId=7"));
        assertThat(calls.getValue().get("PROJECT_TASK")).isEqualTo(new Fanout.Call("prj",
                "/api/v1/projects/tasks/calendar?from=2026-11-01&to=2026-11-30&companyId=7"));
        assertThat(v.events()).singleElement().satisfies(e -> {
            assertThat(e.title()).isEqualTo("SI-2026-11-001 – Test Co");
            assertThat(e.start()).isEqualTo(LocalDate.of(2026, 11, 24));
        });
        assertThat(v.unavailable()).containsOnlyKeys(EKind.PROJECT_TASK);
        assertThat(v.counts()).containsOnlyKeys(EKind.SALES_INVOICE);
    }

    @Test
    void noKindsMeansEveryKind() {
        org.mockito.ArgumentCaptor<Map<String, Fanout.Call>> calls = org.mockito.ArgumentCaptor.captor();
        when(fanout.all(calls.capture())).thenReturn(Map.of());
        var v = new CalendarService(fanout, urls, clock).calendar(null, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), java.util.List.of());
        assertThat(calls.getValue()).hasSize(EKind.values().length);
        assertThat(calls.getValue().get("CONTRACT_END").uri())
                .isEqualTo("/api/v1/crm/contracts?endFrom=2026-11-01&endTo=2026-11-30&size=1000&sort=endDate");
        assertThat(calls.getValue().get("LEAD_TASK").uri()).contains("subject=LEAD");
        assertThat(v.unavailable()).hasSize(EKind.values().length);
    }

    @Test
    void newKindsMapToEvents() throws Exception {
        assertThat(CalendarService.events(EKind.PURCHASE_INVOICE, json("[{'id':'4','number':'PI-1','vendorName':'V',"
                + "'dueDate':'2026-11-10','status':'DRAFT'}]"))).singleElement()
                .satisfies(e -> assertThat(e.title() + "|" + e.status()).isEqualTo("PI-1 – V|DRAFT"));

        assertThat(CalendarService.events(EKind.EVENT, json("[{'id':'5','title':'Year end party','startDate':'2026-11-27',"
                + "'endDate':'2026-11-28','startTime':'18:00','endTime':'22:00','status':'APPROVED'},"
                + "{'id':'6','title':'Expo','startDate':'2026-11-02','endDate':'2026-11-02'}]")))
                .extracting(e -> e.id() + ":" + e.allDay() + ":" + e.end()).containsExactly("5:false:2026-11-28",
                        "6:true:2026-11-02");

        assertThat(CalendarService.events(EKind.DEAL_TASK, json("[{'id':'8','subjectName':'Big deal','name':'Call',"
                + "'date':'2026-11-12','time':'14:30','status':'OPEN'},{'id':'9','subjectName':'Big deal',"
                + "'name':'Mail','date':'2026-11-13','status':'DONE'}]")))
                .extracting(e -> e.kind() + ":" + e.title() + ":" + e.startTime() + ":" + e.allDay())
                .containsExactly("DEAL_TASK:Call – Big deal:14:30:false", "DEAL_TASK:Mail – Big deal:null:true");

        assertThat(CalendarService.events(EKind.CONTRACT_END, json("{'content':[{'id':'10','number':'CON0001',"
                + "'subject':'Support','customerName':'Test Co','endDate':'2026-11-30','status':'ACTIVE'}]}")))
                .singleElement().satisfies(e -> {
                    assertThat(e.title()).isEqualTo("CON0001 – Support – Test Co");
                    assertThat(e.start()).isEqualTo(LocalDate.of(2026, 11, 30));
                });

        assertThat(CalendarService.events(EKind.PROJECT_TASK, json("[{'id':'11','projectId':'2','projectName':'ERP',"
                + "'title':'Spec','startDate':'2026-11-01','dueDate':'2026-11-15','stageName':'Doing','done':false},"
                + "{'id':'12','projectName':'ERP','title':'Old','dueDate':'2026-11-03','done':true},"
                + "{'id':'13','projectName':'ERP','title':'No due','startDate':'2026-11-03','dueDate':null}]")))
                .extracting(e -> e.id() + ":" + e.title() + ":" + e.start() + ":" + e.status())
                .containsExactly("11:ERP – Spec:2026-11-15:Doing", "12:ERP – Old:2026-11-03:DONE");

        assertThat(CalendarService.events(EKind.ZOOM_MEETING, json("[{'id':'14','topic':'Sync','startDate':'2026-11-05',"
                + "'startTime':'09:00','endTime':'09:45','status':'SCHEDULED'},{'id':'15','topic':'X',"
                + "'startDate':'2026-11-05','startTime':'10:00','endTime':'10:30','status':'CANCELLED'}]")))
                .singleElement().satisfies(e -> assertThat(e.endTime()).hasToString("09:45"));

        assertThat(CalendarService.events(EKind.INTERVIEW, json("{'content':[{'id':'16','candidate':{'fullName':'Test "
                + "Candidate'},'round':{'name':'HR'},'date':'2026-11-06','startTime':'10:00','durationMinutes':60,"
                + "'status':'SCHEDULED'},{'id':'17','candidate':{'fullName':'C'},'date':'2026-11-06',"
                + "'startTime':'11:00','durationMinutes':30,'status':'CANCELLED'}]}")))
                .singleElement().satisfies(e -> {
                    assertThat(e.title()).isEqualTo("Test Candidate – HR");
                    assertThat(e.endTime()).hasToString("11:00");
                });
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
