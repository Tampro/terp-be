package com.terp.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Call;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.config.ReportProperties;
import com.terp.erp.report.dashboard.HrDashboardService;
import com.terp.erp.report.dashboard.ModuleDashboardService;
import com.terp.erp.report.dashboard.ReportController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleDashboardServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Fanout fanout = mock(Fanout.class);
    private final ReportProperties urls = new ReportProperties("i", "e", "a", "r", "p", "s", "m", "c", "crm", "prj",
            "acc", "inv", "sup");
    private final ModuleDashboardService service = new ModuleDashboardService(fanout, urls, mapper);
    private final List<Map<String, Call>> seen = new ArrayList<>();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s.replace('\'', '"'));
    }

    /** Answers each call key from the given parts (IAM settings under "settings"); records every fan-out. */
    private void answer(Map<String, Part> byKey) {
        when(fanout.all(any())).thenAnswer(inv -> {
            Map<String, Call> calls = inv.getArgument(0);
            seen.add(calls);
            Map<String, Part> out = new LinkedHashMap<>();
            calls.keySet().forEach(k -> out.put(k, byKey.get(k)));
            return out;
        });
    }

    private Part settings(String tz) throws Exception {
        return new Part(json("{'system':{'defaultTimezone':'" + tz + "'}}"), null);
    }

    @Test
    void projectDashboardPassesTheCompanyZoneAndTheYear() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("settings", settings("Asia/Seoul"));
        p.put("stats", new Part(json("{'scope':'ALL','totalProjects':3,'taskCompletionPercent':50}"), null));
        answer(p);

        var d = service.project(7L, 2026);

        assertThat(d.timezone()).isEqualTo("Asia/Seoul");
        assertThat(d.timezoneSource()).isEqualTo("COMPANY");
        assertThat(d.unavailable()).isEmpty();
        assertThat(d.parts().get("stats").data().path("totalProjects").asInt()).isEqualTo(3);
        assertThat(seen.get(0).get("settings")).isEqualTo(new Call("i",
                "/api/v1/settings/company/effective?companyId=7"));
        assertThat(seen.get(1).get("stats")).isEqualTo(new Call("prj",
                "/api/v1/projects/stats?companyId=7&year=2026&tz=Asia/Seoul"));
    }

    @Test
    void iamDownFallsBackToTheDefaultZoneFlagged() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("settings", new Part(null, Fanout.UNAVAILABLE));
        p.put("stats", new Part(json("{'total':1}"), null));
        answer(p);

        var d = service.support(null, null);

        assertThat(d.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(d.timezoneSource()).isEqualTo("DEFAULT");
        assertThat(seen.get(1).get("stats")).isEqualTo(new Call("sup",
                "/api/v1/support/tickets/stats?tz=Asia/Ho_Chi_Minh"));
    }

    @Test
    void posDashboardDegradesPerSourceNeverToZero() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("settings", settings("Asia/Ho_Chi_Minh"));
        p.put("sales", new Part(json("{'todayOrders':2,'heatmap':[]}"), null));
        p.put("stock", new Part(null, "COMMON_004"));
        answer(p);

        var d = service.pos(7L, null);

        assertThat(d.parts().get("sales").data().path("todayOrders").asInt()).isEqualTo(2);
        assertThat(d.parts().get("stock").data()).isNull();
        assertThat(d.unavailable()).containsOnly(Map.entry("stock", "COMMON_004"));
        assertThat(seen.get(1).get("stock")).isEqualTo(new Call("inv", "/api/v1/inventory/stock/summary?companyId=7"));
        assertThat(seen.get(1).get("sales").uri()).startsWith("/api/v1/pos/reports/dashboard?companyId=7&tz=");
    }

    @Test
    void accountDashboardNeedsNoZoneAndAMissingPartIsUnavailable() {
        answer(new LinkedHashMap<>());

        var d = service.account(7L, 2025);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).get("stats")).isEqualTo(new Call("acc",
                "/api/v1/accounting/reports/dashboard?companyId=7&year=2025"));
        assertThat(d.timezone()).isNull();
        assertThat(d.unavailable()).containsOnly(Map.entry("stats", Fanout.UNAVAILABLE));
    }

    @Test
    void crmDashboardCallsTheCrmAggregate() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("settings", settings("Asia/Ho_Chi_Minh"));
        p.put("stats", new Part(json("{'deals':5,'upcomingTasks':[]}"), null));
        answer(p);

        var d = service.crm(null);

        assertThat(d.parts().get("stats").data().path("deals").asInt()).isEqualTo(5);
        assertThat(seen.get(1).get("stats").uri()).isEqualTo("/api/v1/crm/dashboard?tz=Asia/Ho_Chi_Minh");
    }

    @Test
    void clientDashboardCountsDealsAndOpenTickets() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("payments", new Part(json("{'totalSettled':1500,'notesTotal':100}"), null));
        p.put("deals", new Part(json("{'content':[{}],'totalElements':4}"), null));
        p.put("inProgress", new Part(json("{'content':[],'totalElements':2}"), null));
        p.put("onHold", new Part(json("{'content':[],'totalElements':1}"), null));
        answer(p);

        var d = service.client(2026);

        assertThat(d.parts().get("payments").data().path("totalSettled").asInt()).isEqualTo(1500);
        assertThat(d.parts().get("deals").data().path("count").asLong()).isEqualTo(4);
        assertThat(d.parts().get("openTickets").data().path("count").asLong()).isEqualTo(3);
        assertThat(d.unavailable()).isEmpty();
        assertThat(seen.get(0).get("payments")).isEqualTo(new Call("acc",
                "/api/v1/portal/client/payments/summary?year=2026"));
    }

    @Test
    void oneFailingTicketCountMakesOpenTicketsUnavailableNotHalf() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("payments", new Part(null, "ACC_098"));
        p.put("deals", new Part(json("{'totalElements':0}"), null));
        p.put("inProgress", new Part(json("{'totalElements':2}"), null));
        p.put("onHold", new Part(null, Fanout.UNAVAILABLE));
        answer(p);

        var d = service.client(null);

        assertThat(d.parts().get("openTickets").data()).isNull();
        assertThat(d.unavailable()).containsEntry("openTickets", Fanout.UNAVAILABLE)
                .containsEntry("payments", "ACC_098").doesNotContainKey("deals");
    }

    @Test
    void vendorDashboardFlagsRequestsAsNotAvailable() throws Exception {
        Map<String, Part> p = new LinkedHashMap<>();
        p.put("payments", new Part(json("{'totalSettled':900}"), null));
        p.put("purchaseInvoices", new Part(json("{'totalElements':6}"), null));
        answer(p);

        var d = service.vendor(null);

        assertThat(d.parts().get("purchaseInvoices").data().path("count").asLong()).isEqualTo(6);
        assertThat(d.unavailable()).containsOnly(Map.entry("requests", HrDashboardService.NOT_AVAILABLE));
        assertThat(seen.get(0).get("payments").uri()).isEqualTo("/api/v1/portal/vendor/payments/summary");
    }

    @Test
    void everyNewDashboardHandlerChecksItsPermission() throws Exception {
        Map<String, String> expected = Map.of(
                "projectDashboard", "@PermissionChecker.hasAny('PROJECT_VIEW','PROJECT_VIEW_OWN')",
                "accountDashboard", "@PermissionChecker.has('ACCOUNTING_REPORT_VIEW')",
                "posDashboard", "@PermissionChecker.has('POS_REPORT')",
                "crmDashboard", "@PermissionChecker.hasAny('CRM_VIEW','CRM_VIEW_OWN')",
                "supportDashboard", "@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')",
                "clientDashboard", "@PermissionChecker.has('PORTAL_CLIENT')",
                "vendorDashboard", "@PermissionChecker.has('PORTAL_VENDOR')");
        for (var m : ReportController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                assertThat(pa).as(m.getName()).isNotNull();
                if (expected.containsKey(m.getName())) {
                    assertThat(pa.value()).as(m.getName()).isEqualTo(expected.get(m.getName()));
                }
            }
        }
    }
}
