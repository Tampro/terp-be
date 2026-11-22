package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.BalanceSheetLine.ESection;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Balance sheet snapshots (ERP manual 13, FR-DE-005): balances as of a date, grouped into sections, saved as DRAFT
 * (regenerate, notes, delete) and then FINALISED (locked). Two finalised sheets can be compared. The vertical and
 * horizontal layouts are FE views of the same sections.
 * <p>
 * TODO(assumption): VAS B01-DN grouping by account code — assets 1xx current, 21x fixed, other 2xx other long-term;
 * liabilities 34x long-term, the rest current; equity = every EQUITY account plus revenue - expenses not yet closed
 * (current-year profit). Confirm with the chief accountant (Q-07).
 */
@Service
@RequiredArgsConstructor
public class BalanceSheetService {
    static final String CURRENT_PROFIT = "Current year profit (not closed)";

    private final BalanceSheetRepository sheets;
    private final LedgerReportService reports;
    private final Clock clock;

    public record GenerateForm(
            @Schema(description = "Balances as of this date (end of day)", example = "2026-12-31",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(description = "Financial year the date belongs to", example = "2026",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1900) @Max(9999) Integer financialYear,
            @Schema(description = "Notes", example = "Before audit adjustments") @Size(max = 2000) String notes) {
    }

    public record NotesForm(
            @Schema(description = "Notes; null or blank clears them", example = "Audited") @Size(max = 2000)
            String notes) {
    }

    public record LineDto(String accountId, String accountCode, String accountName, BigDecimal amount) {
    }

    public record GroupDto(String group, List<LineDto> lines, BigDecimal total) {
    }

    public record SectionDto(ESection section, List<GroupDto> groups, BigDecimal total) {
    }

    /** sections is null in list results. balanced = difference is zero. */
    public record SheetDto(String id, String companyId, LocalDate date, int financialYear,
                           EBalanceSheetStatus status, String notes, BigDecimal totalAssets,
                           BigDecimal totalLiabilities, BigDecimal totalEquity, BigDecimal difference,
                           boolean balanced, List<SectionDto> sections, Instant finalisedAt, String createdBy,
                           Instant createdDate) {
    }

    public record CompareLine(ESection section, String group, String accountCode, String accountName,
                              BigDecimal first, BigDecimal second, BigDecimal change) {
    }

    public record CompareDto(SheetDto first, SheetDto second, List<CompareLine> lines, CompareLine totalAssets,
                             CompareLine totalLiabilities, CompareLine totalEquity) {
    }

    @Transactional
    public SheetDto generate(Long requestedCompanyId, GenerateForm form) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkYear(form.date(), form.financialYear());
        BalanceSheet s = new BalanceSheet();
        s.setCompanyId(company);
        s.setSheetDate(form.date());
        s.setFinancialYear(form.financialYear());
        s.setStatus(EBalanceSheetStatus.DRAFT);
        s.setNotes(blankToNull(form.notes()));
        fill(s);
        return dto(sheets.save(s), true);
    }

    @Transactional(readOnly = true)
    public Page<SheetDto> search(Long requestedCompanyId, Integer year, EBalanceSheetStatus status,
                                 Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return sheets.search(company, year, status, pageable).map(s -> dto(s, false));
    }

    @Transactional(readOnly = true)
    public SheetDto get(Long id) {
        return dto(require(id), true);
    }

    /** Recomputes a DRAFT from today's books for its date (entries posted since generation are included). */
    @Transactional
    public SheetDto regenerate(Long id) {
        BalanceSheet s = requireDraft(id);
        fill(s);
        return dto(sheets.save(s), true);
    }

    @Transactional
    public SheetDto updateNotes(Long id, NotesForm form) {
        BalanceSheet s = requireDraft(id);
        s.setNotes(blankToNull(form.notes()));
        return dto(sheets.save(s), true);
    }

    @Transactional
    public void delete(Long id) {
        sheets.delete(requireDraft(id));
    }

    /** DRAFT -> FINALISED; afterwards nothing on the sheet changes. */
    @Transactional
    public SheetDto finalise(Long id) {
        BalanceSheet s = requireDraft(id);
        s.setStatus(EBalanceSheetStatus.FINALISED);
        s.setFinalisedAt(Instant.now(clock));
        return dto(sheets.save(s), true);
    }

    /** Lines matched on section, group and account code; change = second - first. */
    @Transactional(readOnly = true)
    public CompareDto compare(Long firstId, Long secondId) {
        BalanceSheet a = require(firstId);
        BalanceSheet b = require(secondId);
        if (a.getStatus() != EBalanceSheetStatus.FINALISED || b.getStatus() != EBalanceSheetStatus.FINALISED
                || !Objects.equals(a.getCompanyId(), b.getCompanyId())) {
            throw ApiException.badRequest(EAccountingError.ACC_074);
        }
        Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
        Map<String, BalanceSheetLine> meta = new LinkedHashMap<>();
        for (int i = 0; i < 2; i++) {
            for (BalanceSheetLine l : (i == 0 ? a : b).getLines()) {
                String key = l.getSection() + "|" + l.getGroupCode() + "|"
                        + (l.getAccountCode() == null ? "" : l.getAccountCode());
                meta.putIfAbsent(key, l);
                amounts.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[i] =
                        l.getAmount();
            }
        }
        List<CompareLine> lines = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : amounts.entrySet()) {
            BalanceSheetLine m = meta.get(e.getKey());
            BigDecimal[] v = e.getValue();
            lines.add(new CompareLine(m.getSection(), m.getGroupCode(), m.getAccountCode(), m.getAccountName(),
                    v[0], v[1], v[1].subtract(v[0])));
        }
        return new CompareDto(dto(a, false), dto(b, false), lines,
                total(ESection.ASSETS, a.getTotalAssets(), b.getTotalAssets()),
                total(ESection.LIABILITIES, a.getTotalLiabilities(), b.getTotalLiabilities()),
                total(ESection.EQUITY, a.getTotalEquity(), b.getTotalEquity()));
    }

    // ---------- computation ----------

    private void fill(BalanceSheet s) {
        LedgerReportService.Chart chart = reports.chart(s.getCompanyId());
        Map<Long, BigDecimal> net = reports.netAsOf(s.getCompanyId(), s.getSheetDate(), chart);
        s.getLines().clear();
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        List<BalanceSheetLine> lines = new ArrayList<>();
        for (ChartAccount a : chart.accounts().values()) {
            BigDecimal n = net.getOrDefault(a.getId(), BigDecimal.ZERO);
            EAccountCategory category = chart.categoryOf(a);
            if (n.signum() == 0 || category == null) {
                continue;
            }
            switch (category) {
                case ASSETS -> {
                    lines.add(line(ESection.ASSETS, assetGroup(a.getCode()), a, n));
                    assets = assets.add(n);
                }
                case LIABILITIES -> {
                    lines.add(line(ESection.LIABILITIES, liabilityGroup(a.getCode()), a, n.negate()));
                    liabilities = liabilities.add(n.negate());
                }
                case EQUITY -> {
                    lines.add(line(ESection.EQUITY, "EQUITY", a, n.negate()));
                    equity = equity.add(n.negate());
                }
                // Revenue and expense balances still open (not closed to 911 / 4212) are this year's profit.
                case REVENUE, EXPENSES -> profit = profit.add(n.negate());
            }
        }
        if (profit.signum() != 0) {
            BalanceSheetLine p = new BalanceSheetLine();
            p.setSection(ESection.EQUITY);
            p.setGroupCode("EQUITY");
            p.setAccountName(CURRENT_PROFIT);
            p.setAmount(profit);
            lines.add(p);
            equity = equity.add(profit);
        }
        int order = 0;
        for (ESection section : ESection.values()) {
            for (String group : List.of("CURRENT_ASSETS", "OTHER_ASSETS", "FIXED_ASSETS", "CURRENT_LIABILITIES",
                    "LONG_TERM_LIABILITIES", "EQUITY")) {
                for (BalanceSheetLine l : lines) {
                    if (l.getSection() == section && l.getGroupCode().equals(group)) {
                        l.setSortOrder(order++);
                        s.getLines().add(l);
                    }
                }
            }
        }
        s.setTotalAssets(assets);
        s.setTotalLiabilities(liabilities);
        s.setTotalEquity(equity);
        s.setDifference(assets.subtract(liabilities.add(equity)));
    }

    static String assetGroup(String code) {
        if (code.startsWith("21")) {
            return "FIXED_ASSETS";
        }
        return code.startsWith("1") ? "CURRENT_ASSETS" : "OTHER_ASSETS";
    }

    static String liabilityGroup(String code) {
        return code.startsWith("34") ? "LONG_TERM_LIABILITIES" : "CURRENT_LIABILITIES";
    }

    private static BalanceSheetLine line(ESection section, String group, ChartAccount a, BigDecimal amount) {
        BalanceSheetLine l = new BalanceSheetLine();
        l.setSection(section);
        l.setGroupCode(group);
        l.setAccountId(a.getId());
        l.setAccountCode(a.getCode());
        l.setAccountName(a.getName());
        l.setAmount(amount);
        return l;
    }

    // ---------- helpers ----------

    private static void checkYear(LocalDate date, int year) {
        // TODO(assumption): financial year = calendar year.
        if (date.getYear() != year) {
            throw ApiException.badRequest(EAccountingError.ACC_077, date.toString(), String.valueOf(year));
        }
    }

    private BalanceSheet requireDraft(Long id) {
        BalanceSheet s = require(id);
        if (s.getStatus() != EBalanceSheetStatus.DRAFT) {
            throw ApiException.badRequest(EAccountingError.ACC_073);
        }
        return s;
    }

    private BalanceSheet require(Long id) {
        BalanceSheet s = sheets.findById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_072));
        TenantGuard.assertSameCompany(s.getCompanyId(), EAccountingError.ACC_072);
        return s;
    }

    private static CompareLine total(ESection section, BigDecimal first, BigDecimal second) {
        return new CompareLine(section, "TOTAL", null, "Total " + section.name().toLowerCase(), first, second,
                second.subtract(first));
    }

    SheetDto dto(BalanceSheet s, boolean withSections) {
        List<SectionDto> sections = null;
        if (withSections) {
            sections = new ArrayList<>();
            for (ESection section : ESection.values()) {
                Map<String, List<LineDto>> groups = new LinkedHashMap<>();
                for (BalanceSheetLine l : s.getLines()) {
                    if (l.getSection() == section) {
                        groups.computeIfAbsent(l.getGroupCode(), k -> new ArrayList<>()).add(new LineDto(
                                l.getAccountId() == null ? null : l.getAccountId().toString(), l.getAccountCode(),
                                l.getAccountName(), l.getAmount()));
                    }
                }
                List<GroupDto> gs = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                for (Map.Entry<String, List<LineDto>> g : groups.entrySet()) {
                    BigDecimal t = g.getValue().stream().map(LineDto::amount).reduce(BigDecimal.ZERO,
                            BigDecimal::add);
                    gs.add(new GroupDto(g.getKey(), g.getValue(), t));
                    total = total.add(t);
                }
                sections.add(new SectionDto(section, gs, total));
            }
        }
        return new SheetDto(s.getId() == null ? null : s.getId().toString(), s.getCompanyId().toString(),
                s.getSheetDate(), s.getFinancialYear(), s.getStatus(), s.getNotes(), s.getTotalAssets(),
                s.getTotalLiabilities(), s.getTotalEquity(), s.getDifference(), s.getDifference().signum() == 0,
                sections, s.getFinalisedAt(), s.getCreatedBy(), s.getCreatedDate());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
