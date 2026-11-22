package com.terp.erp.payroll.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.config.PayrollProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * accounting-service as the caller: reading a bank account needs BANK_VIEW there, booking a payroll payment needs
 * BANK_MANAGE or PAYROLL_PAY; the company check applies there too.
 */
@Component
public class AccountingClient {
    public record BankAccount(Long id, Long companyId, String bankName, String accountNumber, boolean active) {
    }

    private final CallerHttp http;

    public AccountingClient(PayrollProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.accountingServiceUrl(), mapper, EPayrollError.PAY_041, "accounting-service");
    }

    /** 404 ACC_006 (relayed) when it is not a bank account of the caller's company. */
    public BankAccount bankAccount(Long id) {
        JsonNode d = http.call(c -> c.get().uri("/api/v1/accounting/bank-accounts/{id}", id)).path("data");
        return new BankAccount(d.path("id").asLong(), d.path("companyId").asLong(), d.path("bankName").asText(null),
                d.path("accountNumber").asText(null), d.path("active").asBoolean(false));
    }

    /**
     * The salary expense accrual (Dr 6421 / Cr 334, dated accrualDate), then one bank DEBIT and its journal entry
     * (Dr 334 / Cr bank) for the run; idempotent per runId on the accounting side, so a retry after a lost answer
     * books nothing twice.
     */
    public void payrollPosting(Long companyId, Long runId, Long bankAccountId, BigDecimal amount, LocalDate date,
                               String reference, String description, LocalDate accrualDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("runId", runId);
        body.put("bankAccountId", bankAccountId);
        body.put("amount", amount);
        body.put("date", date.toString());
        body.put("reference", reference);
        body.put("description", description);
        body.put("accrualDate", accrualDate == null ? null : accrualDate.toString());
        http.call(c -> c.post().uri(u -> u.path("/api/v1/accounting/payroll-postings")
                .queryParam("companyId", companyId).build()).body(body));
    }
}
