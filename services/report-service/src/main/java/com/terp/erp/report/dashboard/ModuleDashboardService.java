package com.terp.erp.report.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.terp.erp.report.client.Fanout;
import com.terp.erp.report.client.Fanout.Call;
import com.terp.erp.report.client.Fanout.Part;
import com.terp.erp.report.config.ReportProperties;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The module dashboards of manual §5 (project, account, POS, CRM, support) and the client / vendor portal views.
 * Every figure is computed by the service that owns the rows (its aggregate endpoint, called with the caller's token,
 * so its permission, company and own-scope checks apply); this class only fans out and labels the parts. A part
 * whose source refused or failed is data = null and listed in {@code unavailable} with the reason, never a zero.
 */
@Service
@RequiredArgsConstructor
public class ModuleDashboardService {
    /**
     * TODO(assumption): used only when IAM's effective settings cannot be read; the company's own value
     * (settings/company/effective system.defaultTimezone) wins otherwise.
     */
    static final String DEFAULT_ZONE = "Asia/Ho_Chi_Minh";

    private final Fanout fanout;
    private final ReportProperties urls;
    private final ObjectMapper mapper;

    @Schema(description = "A module dashboard: named parts (data, or data = null with error = the reason), the "
            + "time zone the figures were bucketed in, and every part without data in unavailable")
    public record ModuleDashboard(Integer year,
                                  @Schema(description = "IANA zone of today / months / hours") String timezone,
                                  @Schema(description = "COMPANY (IAM setting) or DEFAULT (IAM unreadable)")
                                  String timezoneSource,
                                  Map<String, Part> parts, Map<String, String> unavailable) {
    }

    private record Zone(String id, String source) {
    }

    public ModuleDashboard project(Long companyId, Integer year) {
        Zone z = zone(companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("stats", new Call(urls.projectServiceUrl(), "/api/v1/projects/stats" + q(companyId, year, z)));
        return assemble(year, z, fanout.all(calls));
    }

    public ModuleDashboard account(Long companyId, Integer year) {
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("stats", new Call(urls.accountingServiceUrl(), "/api/v1/accounting/reports/dashboard"
                + q(companyId, year, null)));
        return assemble(year, null, fanout.all(calls));
    }

    public ModuleDashboard pos(Long companyId, Integer year) {
        Zone z = zone(companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("sales", new Call(urls.commerceServiceUrl(), "/api/v1/pos/reports/dashboard"
                + q(companyId, year, z)));
        calls.put("stock", new Call(urls.inventoryServiceUrl(), "/api/v1/inventory/stock/summary"
                + q(companyId, null, null)));
        return assemble(year, z, fanout.all(calls));
    }

    public ModuleDashboard crm(Long companyId) {
        Zone z = zone(companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("stats", new Call(urls.crmServiceUrl(), "/api/v1/crm/dashboard" + q(companyId, null, z)));
        return assemble(null, z, fanout.all(calls));
    }

    public ModuleDashboard support(Long companyId, Integer year) {
        Zone z = zone(companyId);
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("stats", new Call(urls.supportServiceUrl(), "/api/v1/support/tickets/stats"
                + q(companyId, year, z)));
        return assemble(year, z, fanout.all(calls));
    }

    /** Client portal view (manual §5 role views): payments, credit notes, deals, open tickets of my customer. */
    public ModuleDashboard client(Integer year) {
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("payments", new Call(urls.accountingServiceUrl(), "/api/v1/portal/client/payments/summary"
                + q(null, year, null)));
        calls.put("deals", new Call(urls.crmServiceUrl(), "/api/v1/portal/client/deals?size=1"));
        calls.put("inProgress", new Call(urls.supportServiceUrl(), "/api/v1/portal/client/tickets?status=IN_PROGRESS&size=1"));
        calls.put("onHold", new Call(urls.supportServiceUrl(), "/api/v1/portal/client/tickets?status=ON_HOLD&size=1"));
        Map<String, Part> p = fanout.all(calls);
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("payments", orUnavailable(p.get("payments")));
        parts.put("deals", count(p.get("deals")));
        parts.put("openTickets", sumCounts(p.get("inProgress"), p.get("onHold")));
        return new ModuleDashboard(year, null, null, parts, HrDashboardService.unavailable(parts));
    }

    /** Vendor portal view: payments received, debit notes, purchase invoices of my vendor. */
    public ModuleDashboard vendor(Integer year) {
        Map<String, Call> calls = new LinkedHashMap<>();
        calls.put("payments", new Call(urls.accountingServiceUrl(), "/api/v1/portal/vendor/payments/summary"
                + q(null, year, null)));
        calls.put("purchaseInvoices", new Call(urls.commerceServiceUrl(),
                "/api/v1/portal/vendor/purchase-invoices?size=1"));
        Map<String, Part> p = fanout.all(calls);
        Map<String, Part> parts = new LinkedHashMap<>();
        parts.put("payments", orUnavailable(p.get("payments")));
        parts.put("purchaseInvoices", count(p.get("purchaseInvoices")));
        // TODO(assumption): the manual's vendor "requests" card names no record type (RFQ? purchase requests?);
        // nothing in the ERP matches it yet, so it is reported as not available rather than guessed.
        parts.put("requests", new Part(null, HrDashboardService.NOT_AVAILABLE));
        return new ModuleDashboard(year, null, null, parts, HrDashboardService.unavailable(parts));
    }

    /** The company's time zone from IAM; the default (flagged DEFAULT) when IAM does not answer. */
    Zone zone(Long companyId) {
        Part p = fanout.all(Map.of("settings", new Call(urls.iamServiceUrl(),
                "/api/v1/settings/company/effective" + (companyId == null ? "" : "?companyId=" + companyId))))
                .get("settings");
        String id = p == null || p.data() == null ? null
                : p.data().path("system").path("defaultTimezone").asText(null);
        if (id != null && !id.isBlank()) {
            try {
                return new Zone(ZoneId.of(id).getId(), "COMPANY");
            } catch (DateTimeException ignored) {
                // a value IAM accepted earlier but this JVM does not know: fall back, flagged
            }
        }
        return new Zone(DEFAULT_ZONE, "DEFAULT");
    }

    private ModuleDashboard assemble(Integer year, Zone z, Map<String, Part> raw) {
        Map<String, Part> parts = new LinkedHashMap<>();
        raw.forEach((k, v) -> parts.put(k, orUnavailable(v)));
        return new ModuleDashboard(year, z == null ? null : z.id(), z == null ? null : z.source(), parts,
                HrDashboardService.unavailable(parts));
    }

    private static String q(Long companyId, Integer year, Zone z) {
        StringBuilder b = new StringBuilder();
        if (companyId != null) {
            b.append("&companyId=").append(companyId);
        }
        if (year != null) {
            b.append("&year=").append(year);
        }
        if (z != null) {
            // Raw: RestClient.uri(String) encodes the query once; pre-encoding made "Asia%2FHo_Chi_Minh" arrive literally.
            b.append("&tz=").append(z.id());
        }
        return b.isEmpty() ? "" : "?" + b.substring(1);
    }

    static Part orUnavailable(Part p) {
        return p == null ? new Part(null, Fanout.UNAVAILABLE) : p;
    }

    /** A paged list read with size=1 for its totalElements only → {count}. */
    Part count(Part p) {
        if (p == null || p.data() == null) {
            return orUnavailable(p);
        }
        JsonNode total = p.data().get("totalElements");
        if (total == null || !total.isNumber()) {
            return new Part(null, Fanout.UNAVAILABLE);
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("count", total.asLong());
        return new Part(out, null);
    }

    /** Sum of several counted lists; one failing makes the whole sum unavailable (a partial sum would lie). */
    Part sumCounts(Part... lists) {
        long sum = 0;
        for (Part l : List.of(lists)) {
            Part c = count(l);
            if (c.data() == null) {
                return c;
            }
            sum += c.data().path("count").asLong();
        }
        ObjectNode out = mapper.createObjectNode();
        out.put("count", sum);
        return new Part(out, null);
    }
}
