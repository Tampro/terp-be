package com.terp.erp.accounting.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.config.AccountingProperties;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * commerce-service as the caller (their token, so commerce applies its own permission and company checks: the caller
 * needs SALES_VIEW / PURCHASE_VIEW to read). Cleared payments reach commerce as events (outbox), not through here. SALES = sales invoices and returns
 * (customers), PURCHASE = purchase invoices and returns (vendors).
 */
@Component
public class CommerceClient {
    public enum ESide {
        SALES("/api/v1/sales/invoices", "/api/v1/sales/returns", "document", "customerId", "customerName",
                "docDate"),
        PURCHASE("/api/v1/purchase/invoices", "/api/v1/purchase/returns", "invoice", "vendorId", "vendorName",
                "invoiceDate");

        final String invoices;
        final String returns;
        final String detailKey;
        final String partyId;
        final String partyName;
        final String dateField;

        ESide(String invoices, String returns, String detailKey, String partyId, String partyName, String dateField) {
            this.invoices = invoices;
            this.returns = returns;
            this.detailKey = detailKey;
            this.partyId = partyId;
            this.partyName = partyName;
            this.dateField = dateField;
        }
    }

    /** status: DRAFT, POSTED, PARTIAL, PAID. date = invoice date. */
    public record Invoice(Long id, String number, Long partyId, String partyName, LocalDate date, LocalDate dueDate,
                          BigDecimal tax, BigDecimal total, BigDecimal balance, String status) {
        public boolean open() {
            return "POSTED".equals(status) || "PARTIAL".equals(status);
        }
    }

    /** status: DRAFT, APPROVED, COMPLETED. tax is known only when read by id (null in lists). */
    public record ReturnDoc(Long id, String number, Long invoiceId, Long partyId, String partyName, LocalDate date,
                            BigDecimal total, BigDecimal tax, String status) {
    }

    /** Page size of list reads; commerce pages are walked to the end. */
    static final int PAGE = 500;

    private final CallerHttp http;

    public CommerceClient(AccountingProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.commerceServiceUrl(), mapper, EAccountingError.ACC_060, "commerce-service");
    }

    public Invoice invoice(ESide side, Long id) {
        JsonNode data = http.call(c -> c.get().uri(side.invoices + "/{id}", id)).path("data");
        return invoice(side, data.path(side.detailKey));
    }

    /**
     * Every invoice of the company (optionally of one party) in a status, with the invoice date in [from, to]
     * (either may be null).
     */
    public List<Invoice> invoices(ESide side, Long companyId, Long partyId, String status, LocalDate from,
                                  LocalDate to) {
        List<Invoice> out = new ArrayList<>();
        for (JsonNode n : list(side.invoices, side.partyId, companyId, partyId, status, from, to)) {
            out.add(invoice(side, n));
        }
        return out;
    }

    public ReturnDoc returnDoc(ESide side, Long id) {
        JsonNode data = http.call(c -> c.get().uri(side.returns + "/{id}", id)).path("data");
        JsonNode r = data.path("ret");
        return returnDoc(side, r, decimal(data.path("tax")));
    }

    public List<ReturnDoc> returns(ESide side, Long companyId, Long partyId, String status, LocalDate from,
                                   LocalDate to) {
        List<ReturnDoc> out = new ArrayList<>();
        for (JsonNode n : list(side.returns, side.partyId, companyId, partyId, status, from, to)) {
            out.add(returnDoc(side, n, null));
        }
        return out;
    }

    private List<JsonNode> list(String path, String partyParam, Long companyId, Long partyId, String status,
                                LocalDate from, LocalDate to) {
        List<JsonNode> rows = new ArrayList<>();
        for (int page = 0; ; page++) {
            int p = page;
            JsonNode data = http.call(c -> c.get().uri(b -> query(b.path(path), partyParam, companyId, partyId,
                    status, from, to, p))).path("data");
            data.path("content").forEach(rows::add);
            if (data.path("last").asBoolean(true) || data.path("content").isEmpty()) {
                return rows;
            }
        }
    }

    private static URI query(UriBuilder b, String partyParam, Long companyId, Long partyId, String status,
                             LocalDate from, LocalDate to, int page) {
        if (companyId != null) {
            b.queryParam("companyId", companyId);
        }
        if (partyId != null) {
            b.queryParam(partyParam, partyId);
        }
        if (status != null) {
            b.queryParam("status", status);
        }
        if (from != null) {
            b.queryParam("from", from);
        }
        if (to != null) {
            b.queryParam("to", to);
        }
        return b.queryParam("page", page).queryParam("size", PAGE).queryParam("sort", "id").build();
    }

    private static Invoice invoice(ESide side, JsonNode n) {
        return new Invoice(n.path("id").asLong(), n.path("number").asText(null), n.path(side.partyId).asLong(),
                n.path(side.partyName).asText(null), date(n.path(side.dateField)), date(n.path("dueDate")),
                decimal(n.path("tax")), decimal(n.path("total")), decimal(n.path("balance")),
                n.path("status").asText(null));
    }

    private static ReturnDoc returnDoc(ESide side, JsonNode n, BigDecimal tax) {
        return new ReturnDoc(n.path("id").asLong(), n.path("number").asText(null), n.path("invoiceId").asLong(),
                n.path(side.partyId).asLong(), n.path(side.partyName).asText(null), date(n.path("returnDate")),
                decimal(n.path("total")), tax, n.path("status").asText(null));
    }

    private static LocalDate date(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? null : LocalDate.parse(n.asText());
    }

    private static BigDecimal decimal(JsonNode n) {
        return n.isMissingNode() || n.isNull() ? BigDecimal.ZERO : new BigDecimal(n.asText());
    }
}
