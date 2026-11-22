package com.terp.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.config.ReportProperties;
import com.terp.erp.report.dashboard.HrDashboardService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HrDashboardServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Fanout fanout = mock(Fanout.class);
    private final ReportProperties urls = new ReportProperties("i", "e", "a", "r", "p", "s", "m", "c", "crm", "prj");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final HrDashboardService service = new HrDashboardService(fanout, urls, mapper, clock);

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s.replace('\'', '"'));
    }

    private Map<String, Part> allOk() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("attendance", new Part(json("{'today':'2026-11-22','presentToday':8,'activeEmployees':10,"
                + "'absentToday':1,'onLeaveToday':1,'notMarkedToday':0}"), null));
        p.put("branches", new Part(json("{'content':[{'id':'1','name':'HQ','employeeCount':7},"
                + "{'id':'2','name':'Branch Two','employeeCount':3}]}"), null));
        p.put("employees", new Part(json("{'byDepartment':[{'departmentId':'4','name':'Sales','employees':6}]}"),
                null));
        p.put("promotions", new Part(json("{'content':[{'id':'11'}]}"), null));
        p.put("terminations", new Part(json("{'content':[]}"), null));
        p.put("events", new Part(json("[{'id':'1'},{'id':'2'},{'id':'3'},{'id':'4'},{'id':'5'},{'id':'6'}]"), null));
        p.put("leaves", new Part(json("{'content':[{'id':'21'}]}"), null));
        p.put("announcements", new Part(json("{'content':[{'id':'31','status':'ACTIVE'}]}"), null));
        return p;
    }

    @Test
    void hrDashboardMapsEveryPartAndCallsTheSourcesAsTheCaller() throws Exception {
        ArgumentCaptor<Map<String, Fanout.Call>> calls = ArgumentCaptor.captor();
        when(fanout.all(calls.capture())).thenReturn(allOk());

        var d = service.hrDashboard(7L);

        assertThat(d.unavailable()).isEmpty();
        assertThat(d.attendanceToday().data().path("attendanceRateToday").decimalValue()).isEqualByComparingTo("80");
        assertThat(d.attendanceToday().data().path("absentToday").asLong()).isEqualTo(1);
        assertThat(d.missingAttendanceToday().data().path("count").asLong()).isZero();
        assertThat(d.employeesByBranch().data()).hasSize(2);
        assertThat(d.employeesByBranch().data().get(0).path("employees").asLong()).isEqualTo(7);
        assertThat(d.employeesByBranch().data().get(1).path("name").asText()).isEqualTo("Branch Two");
        assertThat(d.employeesByDepartment().data().get(0).path("name").asText()).isEqualTo("Sales");
        assertThat(d.recentPromotions().data()).hasSize(1);
        assertThat(d.upcomingEvents().data()).hasSize(5);
        assertThat(d.activeAnnouncements().data().get(0).path("id").asText()).isEqualTo("31");
        assertThat(calls.getValue().get("announcements")).isEqualTo(new Fanout.Call("e",
                "/api/v1/org/announcements?status=ACTIVE&size=5&sort=startDate,desc&companyId=7"));
        assertThat(calls.getValue().get("events")).isEqualTo(new Fanout.Call("m",
                "/api/v1/meeting/events/calendar?from=2026-11-22&to=2026-12-22&companyId=7"));
        assertThat(calls.getValue().get("attendance")).isEqualTo(new Fanout.Call("a",
                "/api/v1/attendance/dashboard?companyId=7"));
    }

    @Test
    void eachPartDegradesOnItsOwn() throws Exception {
        Map<String, Part> p = allOk();
        p.put("attendance", new Part(null, "COMMON_004"));
        p.put("branches", new Part(null, Fanout.UNAVAILABLE));
        p.remove("leaves");
        when(fanout.all(any())).thenReturn(p);

        var d = service.hrDashboard(null);

        assertThat(d.attendanceToday().data()).isNull();
        assertThat(d.unavailable()).containsEntry("attendanceToday", "COMMON_004")
                .containsEntry("missingAttendanceToday", "COMMON_004")
                .containsEntry("employeesByBranch", Fanout.UNAVAILABLE)
                .containsEntry("recentLeaves", Fanout.UNAVAILABLE).hasSize(4);
        assertThat(d.recentLeaves().error()).isEqualTo(Fanout.UNAVAILABLE);
        assertThat(d.employeesByDepartment().ok()).isTrue();
        assertThat(d.recentPromotions().data()).hasSize(1);
    }

    @Test
    void unknownHeadcountLeavesTheRateNullAndMissingUnavailable() throws Exception {
        Map<String, Part> p = allOk();
        p.put("attendance", new Part(json("{'presentToday':3,'activeEmployees':null,'notMarkedToday':null}"), null));
        when(fanout.all(any())).thenReturn(p);

        var d = service.hrDashboard(null);

        assertThat(d.attendanceToday().data().get("attendanceRateToday").isNull()).isTrue();
        assertThat(d.unavailable()).containsOnlyKeys("missingAttendanceToday");
    }

    private Map<String, Part> mine() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("profile", new Part(json("{'id':'12','employeeNo':'EMP-0012','fullName':'Test Twelve',"
                + "'branch':{'id':'1','name':'HQ'},'department':{'id':'4','name':'Sales'},'designation':null,"
                + "'banking':{'accountNo':'0000'},'addressLine1':'Fake street'}"), null));
        p.put("today", new Part(json("{'date':'2026-11-22','record':{'id':'5','status':'PRESENT'},"
                + "'shift':{'id':'3','name':'Day'},'canClockIn':false,'canClockOut':true}"), null));
        p.put("warnings", new Part(json("{'content':[{'id':'41','status':'PENDING'}]}"), null));
        p.put("announcements", new Part(json("{'content':[{'id':'31'},{'id':'32'}]}"), null));
        return p;
    }

    @Test
    void myDashboardMapsTheSelfEndpointsAndNamesTheGaps() throws Exception {
        ArgumentCaptor<Map<String, Fanout.Call>> calls = ArgumentCaptor.captor();
        when(fanout.all(calls.capture())).thenReturn(mine());

        var d = service.myDashboard();

        JsonNode profile = d.parts().get("myProfile").data();
        assertThat(profile.path("employeeNo").asText()).isEqualTo("EMP-0012");
        assertThat(profile.path("fullName").asText()).isEqualTo("Test Twelve");
        assertThat(profile.path("department").path("name").asText()).isEqualTo("Sales");
        assertThat(profile.get("designation").isNull()).isTrue();
        assertThat(profile.has("banking")).isFalse();
        assertThat(profile.has("addressLine1")).isFalse();
        assertThat(d.parts().get("myShift").data().path("name").asText()).isEqualTo("Day");
        JsonNode today = d.parts().get("myAttendanceToday").data();
        assertThat(today.path("canClockOut").asBoolean()).isTrue();
        assertThat(today.path("record").path("status").asText()).isEqualTo("PRESENT");
        assertThat(today.has("shift")).isFalse();
        assertThat(d.parts().get("myWarnings").data()).hasSize(1);
        assertThat(d.parts().get("myAnnouncements").data()).hasSize(2);
        assertThat(d.unavailable()).containsOnlyKeys("myAttendanceThisMonth", "myLeaveBalances",
                "myLeaveApplications", "myAwards", "myComplaints")
                .allSatisfy((k, v) -> assertThat(v).isEqualTo(HrDashboardService.NOT_AVAILABLE));
        assertThat(calls.getValue().get("today")).isEqualTo(new Fanout.Call("a", "/api/v1/attendance/me/today"));
        assertThat(calls.getValue().get("profile")).isEqualTo(new Fanout.Call("e", "/api/v1/employees/me"));
        assertThat(calls.getValue().get("warnings")).isEqualTo(new Fanout.Call("e",
                "/api/v1/lifecycle/warnings/my?size=5&sort=warningDate,desc"));
    }

    @Test
    void aFailingSelfSourceDegradesOnlyItsParts() throws Exception {
        Map<String, Part> p = mine();
        p.put("today", new Part(null, "COMMON_004"));
        p.put("profile", new Part(null, "EMP_041"));
        p.remove("announcements");
        when(fanout.all(any())).thenReturn(p);

        var d = service.myDashboard();

        assertThat(d.unavailable()).containsEntry("myShift", "COMMON_004")
                .containsEntry("myAttendanceToday", "COMMON_004").containsEntry("myProfile", "EMP_041")
                .containsEntry("myAnnouncements", Fanout.UNAVAILABLE).doesNotContainKey("myWarnings").hasSize(9);
        assertThat(d.parts().get("myWarnings").ok()).isTrue();
    }

    @Test
    void noShiftAssignedIsAnEmptyPartNotAFailure() throws Exception {
        Map<String, Part> p = mine();
        p.put("today", new Part(json("{'date':'2026-11-22','record':null,'shift':null,'canClockIn':true,"
                + "'canClockOut':false}"), null));
        when(fanout.all(any())).thenReturn(p);

        var d = service.myDashboard();

        assertThat(d.parts().get("myShift").ok()).isTrue();
        assertThat(d.parts().get("myShift").data().isNull()).isTrue();
        assertThat(d.unavailable()).doesNotContainKey("myShift").hasSize(5);
    }
}
