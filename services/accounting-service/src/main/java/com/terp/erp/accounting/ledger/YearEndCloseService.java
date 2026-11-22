package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
import com.terp.erp.accounting.ledger.JournalService.Line;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Year-end close (ERP manual 13, FR-DE-007). Posts one closing entry (sourceType YEAR_END_CLOSE, sourceId = the year,
 * so it is idempotent): every revenue and expense account's balance at 31 December goes to the income summary (911)
 * and the net of 911 to retained earnings (4212), both through the account mapping. The year is then recorded in
 * fiscal_year_close (once per company and year; no endpoint undoes it) and the journal refuses any entry dated on or
 * before its end.
 * <p>
 * The next year needs no opening entry: account balances are cumulative (opening balance + every counted line), so the
 * balance sheet accounts simply carry on, and the revenue / expense accounts start the new year at zero because the
 * closing entry emptied them.
 */
@Service
@RequiredArgsConstructor
public class YearEndCloseService {
    private final JournalEntryRepository entries;
    private final FiscalYearCloseRepository closes;
    private final AccountMapping mapping;
    private final JournalService journal;
    private final LedgerReportService reports;

    public record CloseForm(
            @Schema(description = "Financial year to close", example = "2026",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1900) @Max(9999) Integer financialYear,
            @Schema(description = "Date of the closing entry, inside the year", example = "2026-12-31",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate closingDate) {
    }

    public record CloseDto(String id, String companyId, int financialYear, LocalDate closingDate, String entryId,
                           String entryNumber, String closedBy, Instant closedAt) {
    }

    /**
     * 400 ACC_077 (closing date outside the year), ACC_075 (already closed), ACC_076 (DRAFT manual entries dated in
     * the year), ACC_070 (a later year is closed already), ACC_017 (911 / 4212 not mapped), ACC_020 (an inactive
     * revenue / expense account still has a balance).
     * TODO(assumption): financial year = calendar year; balances are taken at 31 December even when the closing date
     * is earlier.
     */
    @Transactional
    public CloseDto close(Long requestedCompanyId, CloseForm form) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        int year = form.financialYear();
        if (form.closingDate().getYear() != year) {
            throw ApiException.badRequest(EAccountingError.ACC_077, form.closingDate().toString(),
                    String.valueOf(year));
        }
        if (closes.existsByCompanyIdAndFiscalYear(company, year)) {
            throw ApiException.badRequest(EAccountingError.ACC_075, String.valueOf(year));
        }
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        long drafts = entries.countByCompanyIdAndTypeAndStatusAndEntryDateBetween(company, EJournalType.MANUAL,
                EJournalStatus.DRAFT, start, end);
        if (drafts > 0) {
            throw ApiException.badRequest(EAccountingError.ACC_076, String.valueOf(year), drafts);
        }
        journal.assertOpen(company, form.closingDate());
        Long summary = mapping.require(company, ESystemAccount.INCOME_SUMMARY);
        Long retained = mapping.require(company, ESystemAccount.RETAINED_EARNINGS);

        List<Line> lines = closingLines(company, end, summary, retained);
        JournalEntryDto entry = lines.isEmpty() ? null : journal.post(company, form.closingDate(),
                LedgerReportService.YEAR_END_CLOSE, (long) year, "FY" + year, "Year-end close " + year, lines);
        FiscalYearClose c = new FiscalYearClose();
        c.setCompanyId(company);
        c.setFiscalYear(year);
        c.setClosingDate(form.closingDate());
        c.setEntryId(entry == null ? null : Long.valueOf(entry.id()));
        return dto(closes.save(c), entry == null ? null : entry.number());
    }

    @Transactional(readOnly = true)
    public Page<CloseDto> search(Long requestedCompanyId, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return closes.findByCompanyId(company, pageable).map(c -> dto(c, null));
    }

    /**
     * Each revenue / expense account is brought to zero against 911; then 911's net (profit or loss) goes to 4212.
     * Empty when no revenue / expense account has a balance.
     */
    List<Line> closingLines(Long company, LocalDate end, Long summary, Long retained) {
        LedgerReportService.Chart chart = reports.chart(company);
        Map<Long, BigDecimal> net = reports.netAsOf(company, end, chart);
        List<Line> lines = new ArrayList<>();
        BigDecimal toSummaryCredit = BigDecimal.ZERO;
        BigDecimal toSummaryDebit = BigDecimal.ZERO;
        for (ChartAccount a : chart.accounts().values()) {
            EAccountCategory category = chart.categoryOf(a);
            if (category != EAccountCategory.REVENUE && category != EAccountCategory.EXPENSES) {
                continue;
            }
            if (a.getId().equals(summary) || a.getId().equals(retained)) {
                continue;
            }
            BigDecimal n = net.getOrDefault(a.getId(), BigDecimal.ZERO);
            if (n.signum() < 0) {
                // Credit balance (revenue): debit the account, credit 911.
                lines.add(new Line(a.getId(), n.negate(), null, "Close " + a.getCode()));
                toSummaryCredit = toSummaryCredit.add(n.negate());
            } else if (n.signum() > 0) {
                // Debit balance (expense): credit the account, debit 911.
                lines.add(new Line(a.getId(), null, n, "Close " + a.getCode()));
                toSummaryDebit = toSummaryDebit.add(n);
            }
        }
        if (lines.isEmpty()) {
            return lines;
        }
        if (toSummaryCredit.signum() > 0) {
            lines.add(new Line(summary, null, toSummaryCredit, "Revenue to income summary"));
        }
        if (toSummaryDebit.signum() > 0) {
            lines.add(new Line(summary, toSummaryDebit, null, "Expenses to income summary"));
        }
        BigDecimal profit = toSummaryCredit.subtract(toSummaryDebit);
        if (profit.signum() > 0) {
            lines.add(new Line(summary, profit, null, "Profit to retained earnings"));
            lines.add(new Line(retained, null, profit, "Profit for the year"));
        } else if (profit.signum() < 0) {
            lines.add(new Line(summary, null, profit.negate(), "Loss to retained earnings"));
            lines.add(new Line(retained, profit.negate(), null, "Loss for the year"));
        }
        return lines;
    }

    private static CloseDto dto(FiscalYearClose c, String entryNumber) {
        return new CloseDto(c.getId() == null ? null : c.getId().toString(), c.getCompanyId().toString(),
                c.getFiscalYear(), c.getClosingDate(), c.getEntryId() == null ? null : c.getEntryId().toString(),
                entryNumber, c.getClosedBy(), c.getClosedAt());
    }
}
