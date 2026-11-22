package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.coa.AccountType;
import com.terp.erp.accounting.coa.AccountTypeRepository;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.BalanceSheetLine.ESection;
import com.terp.erp.accounting.ledger.BalanceSheetService.GenerateForm;
import com.terp.erp.accounting.ledger.BalanceSheetService.NotesForm;
import com.terp.erp.accounting.ledger.JournalService.Line;
import com.terp.erp.accounting.ledger.ManualJournalService.LineForm;
import com.terp.erp.accounting.ledger.ManualJournalService.ManualForm;
import com.terp.erp.accounting.ledger.ManualJournalService.ReverseForm;
import com.terp.erp.accounting.ledger.YearEndCloseService.CloseForm;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Manual journal, reversal, period lock, ledger reports, balance sheet and year-end close over an in-memory journal
 * (repositories answered from maps), so the services are exercised together.
 */
@SuppressWarnings("unchecked")
class DoubleEntryTest {
    private static final Long CO = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate DEC = LocalDate.of(2026, 12, 31);

    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final AccountTypeRepository types = mock(AccountTypeRepository.class);
    private final SystemAccountMappingRepository mappingRows = mock(SystemAccountMappingRepository.class);
    private final FiscalYearCloseRepository closes = mock(FiscalYearCloseRepository.class);
    private final BalanceSheetRepository sheets = mock(BalanceSheetRepository.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private final JournalService journal = new JournalService(entries, accounts, numbering, closes);
    private final ManualJournalService manual = new ManualJournalService(entries, accounts, journal, clock);
    private final LedgerReportService reports = new LedgerReportService(entries, accounts, types, mappingRows, clock);
    private final BalanceSheetService balanceSheets = new BalanceSheetService(sheets, reports, clock);
    private final YearEndCloseService yearEnd = new YearEndCloseService(entries, closes, mapping, journal, reports);

    private final Map<Long, JournalEntry> journalRows = new LinkedHashMap<>();
    private final Map<Long, ChartAccount> chart = new LinkedHashMap<>();
    private final Map<Long, BalanceSheet> sheetRows = new HashMap<>();
    private final List<FiscalYearClose> closed = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);
    private final AtomicLong numbers = new AtomicLong();

    private ChartAccount cash;
    private ChartAccount revenue;
    private ChartAccount expense;
    private ChartAccount capital;

    @BeforeEach
    void setUp() {
        TestLogin.as(CO, "LEDGER_MANAGE");
        List<AccountType> typeList = List.of(type(1L, "CA", EAccountCategory.ASSETS),
                type(2L, "LIA", EAccountCategory.LIABILITIES), type(3L, "EQ", EAccountCategory.EQUITY),
                type(4L, "REV", EAccountCategory.REVENUE), type(5L, "COST", EAccountCategory.EXPENSES));
        when(types.findByCompanyId(CO)).thenReturn(typeList);
        cash = account(1L, "111", 1L, ENormalBalance.DEBIT, "1000");
        revenue = account(2L, "5111", 4L, ENormalBalance.CREDIT, "0");
        expense = account(3L, "6428", 5L, ENormalBalance.DEBIT, "0");
        account(4L, "911", 3L, ENormalBalance.CREDIT, "0");
        account(5L, "4212", 3L, ENormalBalance.CREDIT, "0");
        capital = account(6L, "4111", 3L, ENormalBalance.CREDIT, "1000");
        account(7L, "341", 2L, ENormalBalance.CREDIT, "0");

        when(accounts.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(chart.get(i.<Long>getArgument(0))));
        when(accounts.lockById(anyLong())).thenAnswer(i -> Optional.ofNullable(chart.get(i.<Long>getArgument(0))));
        when(accounts.findAllById(any())).thenAnswer(i -> new ArrayList<>(chart.values()));
        when(accounts.findByCompanyIdOrderByCode(CO)).thenAnswer(i -> chart.values().stream()
                .sorted(java.util.Comparator.comparing(ChartAccount::getCode)).toList());
        SystemAccountMapping cashKey = new SystemAccountMapping();
        cashKey.setSystemKey(ESystemAccount.CASH);
        cashKey.setAccountId(1L);
        when(mappingRows.findByCompanyId(CO)).thenReturn(List.of(cashKey));
        when(mapping.require(CO, ESystemAccount.INCOME_SUMMARY)).thenReturn(4L);
        when(mapping.require(CO, ESystemAccount.RETAINED_EARNINGS)).thenReturn(5L);
        when(numbering.next(any(), anyString(), anyString()))
                .thenAnswer(i -> "JE-" + i.getArgument(2) + "-" + String.format("%03d", numbers.incrementAndGet()));

        when(entries.save(any())).thenAnswer(i -> {
            JournalEntry e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(ids.incrementAndGet());
            }
            journalRows.put(e.getId(), e);
            return e;
        });
        when(entries.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(journalRows.get(i.<Long>getArgument(0))));
        when(entries.lockById(anyLong())).thenAnswer(i -> Optional.ofNullable(journalRows.get(i.<Long>getArgument(0))));
        when(entries.findAllById(any())).thenAnswer(i -> {
            List<JournalEntry> out = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                out.add(journalRows.get(id));
            }
            return out;
        });
        org.mockito.Mockito.doAnswer(i -> journalRows.remove(i.<JournalEntry>getArgument(0).getId()))
                .when(entries).delete(any());
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(any(), any(), any())).thenAnswer(i ->
                journalRows.values().stream().filter(e -> i.getArgument(1).equals(e.getSourceType())
                        && i.getArgument(2).equals(e.getSourceId())).findFirst());
        when(entries.lines(any(), any(), any(), any())).thenAnswer(i -> lines(i.getArgument(1), i.getArgument(2),
                i.getArgument(3), null));
        when(entries.accountLines(any(), any(), any(), any(), any())).thenAnswer(i -> lines(i.getArgument(2),
                i.getArgument(3), i.getArgument(4), i.getArgument(1)));
        when(entries.sumsBefore(any(), any(), any())).thenAnswer(i -> {
            Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
            for (LedgerLine l : lines(i.getArgument(1), LocalDate.MIN, i.<LocalDate>getArgument(2).minusDays(1),
                    null)) {
                BigDecimal[] s = sums.computeIfAbsent(l.accountId(), k -> new BigDecimal[]{BigDecimal.ZERO,
                        BigDecimal.ZERO});
                s[0] = s[0].add(l.debit());
                s[1] = s[1].add(l.credit());
            }
            return sums.entrySet().stream().map(e -> new AccountSum(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .toList();
        });
        when(entries.countByCompanyIdAndTypeAndStatusAndEntryDateBetween(any(), any(), any(), any(), any()))
                .thenAnswer(i -> journalRows.values().stream().filter(e -> e.getType() == i.getArgument(1)
                        && e.getStatus() == i.getArgument(2)
                        && !e.getEntryDate().isBefore(i.getArgument(3))
                        && !e.getEntryDate().isAfter(i.getArgument(4))).count());

        when(closes.maxClosedYear(CO)).thenAnswer(i -> closed.stream().map(FiscalYearClose::getFiscalYear)
                .max(Integer::compare).orElse(null));
        when(closes.existsByCompanyIdAndFiscalYear(any(), anyInt())).thenAnswer(i -> closed.stream()
                .anyMatch(c -> c.getFiscalYear() == i.<Integer>getArgument(1)));
        when(closes.save(any())).thenAnswer(i -> {
            FiscalYearClose c = i.getArgument(0);
            c.setId(ids.incrementAndGet());
            closed.add(c);
            return c;
        });

        when(sheets.save(any())).thenAnswer(i -> {
            BalanceSheet s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(ids.incrementAndGet());
            }
            sheetRows.put(s.getId(), s);
            return s;
        });
        when(sheets.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(sheetRows.get(i.<Long>getArgument(0))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------- manual journal ----------

    @Test
    void unbalancedManualEntryRefusedWithDifference() {
        assertThatThrownBy(() -> manual.create(null, form(false, debit(1L, "100"), credit(2L, "90"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException a = (ApiException) e;
                    assertThat(a.getErrorCode()).isEqualTo(EAccountingError.ACC_066);
                    assertThat(a.getArgs()).containsExactly("100", "90", "10");
                });
        assertThat(journalRows).isEmpty();
    }

    @Test
    void draftDoesNotMoveBalancesAndPostDoes() {
        var draft = manual.create(null, form(false, debit(1L, "300"), credit(2L, "300")));
        assertThat(draft.status()).isEqualTo(EJournalStatus.DRAFT);
        assertThat(draft.type()).isEqualTo(EJournalType.MANUAL);
        assertThat(draft.number()).isEqualTo("JE-2026-001");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1000");

        // Edit is a full replacement, still no balance change.
        manual.update(Long.valueOf(draft.id()), form(false, debit(1L, "250"), credit(2L, "250")));
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1000");

        var posted = manual.post(Long.valueOf(draft.id()));
        assertThat(posted.status()).isEqualTo(EJournalStatus.POSTED);
        assertThat(posted.number()).isEqualTo("JE-2026-001");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1250");
        assertThat(revenue.getCurrentBalance()).isEqualByComparingTo("250");
        // A posted entry is no longer editable or deletable.
        assertCode(() -> manual.delete(Long.valueOf(draft.id())), EAccountingError.ACC_067);
    }

    @Test
    void reverseSwapsSidesMarksReversedAndRefusesTwice() {
        var posted = manual.create(null, form(true, debit(1L, "400"), credit(2L, "400")));
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1400");

        var reversal = manual.reverse(Long.valueOf(posted.id()), new ReverseForm(TODAY.plusDays(1), null));
        assertThat(reversal.status()).isEqualTo(EJournalStatus.POSTED);
        assertThat(reversal.reversalOfId()).isEqualTo(posted.id());
        assertThat(reversal.date()).isEqualTo(TODAY.plusDays(1));
        assertThat(reversal.lines()).anySatisfy(l -> {
            assertThat(l.accountId()).isEqualTo("1");
            assertThat(l.credit()).isEqualByComparingTo("400");
        });
        JournalEntry original = journalRows.get(Long.valueOf(posted.id()));
        assertThat(original.getStatus()).isEqualTo(EJournalStatus.REVERSED);
        assertThat(original.getReversedById()).isEqualTo(Long.valueOf(reversal.id()));
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1000");
        assertThat(revenue.getCurrentBalance()).isEqualByComparingTo("0");

        assertCode(() -> manual.reverse(Long.valueOf(posted.id()), null), EAccountingError.ACC_069);
        assertCode(() -> manual.reverse(Long.valueOf(reversal.id()), null), EAccountingError.ACC_069);
    }

    @Test
    void automaticEntryCannotBeEditedOrDeletedButCanBeReversed() {
        var auto = journal.post(CO, TODAY, "REVENUE", 9L, null, null, List.of(
                new Line(1L, new BigDecimal("50"), null, null), new Line(2L, null, new BigDecimal("50"), null)));
        Long id = Long.valueOf(auto.id());
        assertCode(() -> manual.update(id, form(false, debit(1L, "1"), credit(2L, "1"))), EAccountingError.ACC_068);
        assertCode(() -> manual.delete(id), EAccountingError.ACC_068);
        assertCode(() -> manual.post(id), EAccountingError.ACC_068);
        manual.reverse(id, null);
        assertThat(journalRows.get(id).getStatus()).isEqualTo(EJournalStatus.REVERSED);
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1000");
    }

    @Test
    void anotherCompanysEntryIsNotFound() {
        var draft = manual.create(null, form(false, debit(1L, "10"), credit(2L, "10")));
        TestLogin.as(11L, "LEDGER_MANAGE");
        Long id = Long.valueOf(draft.id());
        assertNotFound(() -> manual.post(id), EAccountingError.ACC_021);
        assertNotFound(() -> manual.delete(id), EAccountingError.ACC_021);
        assertNotFound(() -> manual.reverse(id, null), EAccountingError.ACC_021);
        assertNotFound(() -> journal.get(id), EAccountingError.ACC_021);
    }

    // ---------- reports ----------

    @Test
    void trialBalanceTotalsEqualAndReversedPairsCancel() {
        manual.create(null, form(true, debit(1L, "500"), credit(2L, "500")));
        var wrong = manual.create(null, form(true, debit(3L, "70"), credit(1L, "70")));
        manual.reverse(Long.valueOf(wrong.id()), null);
        manual.create(null, form(false, debit(3L, "999"), credit(1L, "999"))); // draft: never counted

        var tb = reports.trialBalance(null, JAN, DEC);
        assertThat(tb.balanced()).isTrue();
        assertThat(tb.difference()).isEqualByComparingTo("0");
        assertThat(tb.totals().closingDebit()).isEqualByComparingTo(tb.totals().closingCredit());
        var expenseRow = tb.rows().stream().filter(r -> r.code().equals("6428")).findFirst().orElseThrow();
        assertThat(expenseRow.periodDebit()).isEqualByComparingTo("70");
        assertThat(expenseRow.periodCredit()).isEqualByComparingTo("70");
        assertThat(expenseRow.closingDebit()).isEqualByComparingTo("0");
        var cashRow = tb.rows().stream().filter(r -> r.code().equals("111")).findFirst().orElseThrow();
        assertThat(cashRow.openingDebit()).isEqualByComparingTo("1000");
        assertThat(cashRow.closingDebit()).isEqualByComparingTo("1500");

        assertCode(() -> reports.trialBalance(null, JAN, JAN.plusDays(400)), EAccountingError.ACC_065);
    }

    @Test
    void profitAndLossNetAndExpenseReport() {
        manual.create(null, form(true, debit(1L, "800"), credit(2L, "800")));
        manual.create(null, form(true, debit(3L, "300"), credit(1L, "300")));
        var pl = reports.profitLoss(null, JAN, DEC);
        assertThat(pl.totalRevenue()).isEqualByComparingTo("800");
        assertThat(pl.totalExpenses()).isEqualByComparingTo("300");
        assertThat(pl.netProfit()).isEqualByComparingTo("500");
        assertThat(pl.expenses()).singleElement().satisfies(g -> assertThat(g.typeCode()).isEqualTo("COST"));
        assertThat(reports.expenseReport(null, JAN, DEC).total()).isEqualByComparingTo("300");

        var cf = reports.cashFlow(null, JAN, DEC);
        assertThat(cf.openingCash()).isEqualByComparingTo("1000");
        assertThat(cf.netChange()).isEqualByComparingTo("500");
        assertThat(cf.closingCash()).isEqualByComparingTo("1500");
    }

    @Test
    void generalLedgerRunningBalanceAndStatementCounterparts() {
        manual.create(null, form(true, LocalDate.of(2026, 3, 1), debit(1L, "200"), credit(2L, "200")));
        manual.create(null, form(true, LocalDate.of(2026, 4, 1), debit(3L, "50"), credit(1L, "50")));
        manual.create(null, form(true, LocalDate.of(2026, 5, 1), debit(1L, "30"), credit(6L, "30")));

        var gl = reports.generalLedger(1L, LocalDate.of(2026, 4, 1), DEC);
        assertThat(gl.openingBalance()).isEqualByComparingTo("1200");
        assertThat(gl.lines()).extracting(LedgerReportService.LedgerRow::balance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1150"), new BigDecimal("1180"));
        assertThat(gl.closingBalance()).isEqualByComparingTo("1180");

        var st = reports.accountStatement(1L, LocalDate.of(2026, 4, 1), DEC);
        assertThat(st.lines().get(0).counterpartCodes()).containsExactly("6428");

        TestLogin.as(11L, "LEDGER_REPORT_VIEW");
        assertNotFound(() -> reports.generalLedger(1L, JAN, DEC), EAccountingError.ACC_010);
    }

    // ---------- balance sheet ----------

    @Test
    void balanceSheetEquationAndFinalisedIsLocked() {
        manual.create(null, form(true, debit(1L, "800"), credit(2L, "800")));
        manual.create(null, form(true, debit(1L, "100"), credit(7L, "100")));
        var sheet = balanceSheets.generate(null, new GenerateForm(DEC, 2026, "first"));
        assertThat(sheet.status()).isEqualTo(EBalanceSheetStatus.DRAFT);
        assertThat(sheet.totalAssets()).isEqualByComparingTo("1900");
        assertThat(sheet.totalLiabilities()).isEqualByComparingTo("100");
        // Equity = capital 1000 + current-year profit 800 not yet closed.
        assertThat(sheet.totalEquity()).isEqualByComparingTo("1800");
        assertThat(sheet.balanced()).isTrue();
        assertThat(sheet.sections()).anySatisfy(s -> {
            assertThat(s.section()).isEqualTo(ESection.LIABILITIES);
            assertThat(s.groups()).singleElement()
                    .satisfies(g -> assertThat(g.group()).isEqualTo("LONG_TERM_LIABILITIES"));
        });

        // An unbalanced opening balance shows as a difference.
        capital.setOpeningBalance(new BigDecimal("900"));
        var regenerated = balanceSheets.regenerate(Long.valueOf(sheet.id()));
        assertThat(regenerated.difference()).isEqualByComparingTo("100");
        assertThat(regenerated.balanced()).isFalse();

        Long id = Long.valueOf(sheet.id());
        balanceSheets.finalise(id);
        assertCode(() -> balanceSheets.regenerate(id), EAccountingError.ACC_073);
        assertCode(() -> balanceSheets.updateNotes(id, new NotesForm("x")), EAccountingError.ACC_073);
        assertCode(() -> balanceSheets.delete(id), EAccountingError.ACC_073);
        assertCode(() -> balanceSheets.finalise(id), EAccountingError.ACC_073);
        assertCode(() -> balanceSheets.generate(null, new GenerateForm(DEC, 2025, null)), EAccountingError.ACC_077);
    }

    @Test
    void compareTwoFinalisedSheets() {
        var first = balanceSheets.generate(null, new GenerateForm(LocalDate.of(2026, 6, 30), 2026, null));
        manual.create(null, form(true, LocalDate.of(2026, 7, 1), debit(1L, "250"), credit(2L, "250")));
        var second = balanceSheets.generate(null, new GenerateForm(DEC, 2026, null));
        Long a = Long.valueOf(first.id());
        Long b = Long.valueOf(second.id());
        assertCode(() -> balanceSheets.compare(a, b), EAccountingError.ACC_074);
        balanceSheets.finalise(a);
        balanceSheets.finalise(b);
        var cmp = balanceSheets.compare(a, b);
        assertThat(cmp.lines()).anySatisfy(l -> {
            assertThat(l.accountCode()).isEqualTo("111");
            assertThat(l.first()).isEqualByComparingTo("1000");
            assertThat(l.second()).isEqualByComparingTo("1250");
            assertThat(l.change()).isEqualByComparingTo("250");
        });
        assertThat(cmp.totalAssets().change()).isEqualByComparingTo("250");
    }

    @Test
    void anotherCompanysSheetIsNotFound() {
        var sheet = balanceSheets.generate(null, new GenerateForm(DEC, 2026, null));
        TestLogin.as(11L, "LEDGER_CLOSE", "LEDGER_REPORT_VIEW");
        Long id = Long.valueOf(sheet.id());
        assertNotFound(() -> balanceSheets.get(id), EAccountingError.ACC_072);
        assertNotFound(() -> balanceSheets.finalise(id), EAccountingError.ACC_072);
        assertNotFound(() -> balanceSheets.delete(id), EAccountingError.ACC_072);
    }

    // ---------- year-end close ----------

    @Test
    void yearEndClosePostsTo911And4212OnceAndLocksThePeriod() {
        manual.create(null, form(true, LocalDate.of(2026, 3, 1), debit(1L, "800"), credit(2L, "800")));
        manual.create(null, form(true, LocalDate.of(2026, 4, 1), debit(3L, "300"), credit(1L, "300")));

        var closedYear = yearEnd.close(null, new CloseForm(2026, DEC));
        JournalEntry entry = journalRows.get(Long.valueOf(closedYear.entryId()));
        assertThat(entry.getSourceType()).isEqualTo("YEAR_END_CLOSE");
        assertThat(entry.getTotalDebit()).isEqualByComparingTo(entry.getTotalCredit());
        assertThat(revenue.getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(expense.getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(chart.get(4L).getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(chart.get(5L).getCurrentBalance()).isEqualByComparingTo("500");
        // P&L of the closed year still shows the year's result: the closing entry is left out.
        assertThat(reports.profitLoss(null, JAN, DEC).netProfit()).isEqualByComparingTo("500");

        assertCode(() -> yearEnd.close(null, new CloseForm(2026, DEC)), EAccountingError.ACC_075);
        // Period lock: manual, automatic and reversal paths all refuse a date in the closed year.
        assertCode(() -> manual.create(null, form(true, DEC, debit(1L, "5"), credit(2L, "5"))),
                EAccountingError.ACC_070);
        assertCode(() -> journal.post(CO, DEC, "REVENUE", 77L, null, null, List.of(
                new Line(1L, BigDecimal.ONE, null, null), new Line(2L, null, BigDecimal.ONE, null))),
                EAccountingError.ACC_070);
        var next = manual.create(null, form(true, LocalDate.of(2027, 1, 2), debit(1L, "5"), credit(2L, "5")));
        assertThat(next.status()).isEqualTo(EJournalStatus.POSTED);
        assertCode(() -> yearEnd.close(null, new CloseForm(2025, LocalDate.of(2025, 12, 31))),
                EAccountingError.ACC_070);
    }

    @Test
    void yearEndCloseRefusedWithDraftsOrDateOutsideTheYear() {
        manual.create(null, form(false, LocalDate.of(2026, 6, 1), debit(1L, "5"), credit(2L, "5")));
        assertCode(() -> yearEnd.close(null, new CloseForm(2026, DEC)), EAccountingError.ACC_076);
        assertCode(() -> yearEnd.close(null, new CloseForm(2026, LocalDate.of(2027, 1, 1))),
                EAccountingError.ACC_077);
        assertThat(closed).isEmpty();
    }

    // ---------- helpers ----------

    private List<LedgerLine> lines(Collection<EJournalStatus> statuses, LocalDate from, LocalDate to, Long account) {
        List<LedgerLine> out = new ArrayList<>();
        for (JournalEntry e : journalRows.values()) {
            if (!statuses.contains(e.getStatus()) || e.getEntryDate().isBefore(from) || e.getEntryDate().isAfter(to)) {
                continue;
            }
            for (JournalLine l : e.getLines()) {
                if (account == null || account.equals(l.getAccountId())) {
                    out.add(new LedgerLine(e.getId(), e.getNumber(), e.getEntryDate(), e.getReference(),
                            e.getDescription(), e.getSourceType(), e.getSourceId(), l.getAccountId(),
                            l.getDescription(), l.getDebit(), l.getCredit()));
                }
            }
        }
        out.sort(java.util.Comparator.comparing(LedgerLine::date).thenComparing(LedgerLine::entryId));
        return out;
    }

    private static ManualForm form(boolean post, LineForm... lines) {
        return form(post, null, lines);
    }

    private static ManualForm form(boolean post, LocalDate date, LineForm... lines) {
        return new ManualForm(date, EReferenceType.ADJUSTMENT, null, "Test adjustment", List.of(lines), post);
    }

    private static LineForm debit(Long account, String amount) {
        return new LineForm(account, null, new BigDecimal(amount), null);
    }

    private static LineForm credit(Long account, String amount) {
        return new LineForm(account, null, null, new BigDecimal(amount));
    }

    private ChartAccount account(Long id, String code, Long typeId, ENormalBalance normal, String opening) {
        ChartAccount a = new ChartAccount();
        a.setId(id);
        a.setCompanyId(CO);
        a.setCode(code);
        a.setName("Account " + code);
        a.setAccountTypeId(typeId);
        a.setNormalBalance(normal);
        a.setOpeningBalance(new BigDecimal(opening));
        a.setCurrentBalance(new BigDecimal(opening));
        chart.put(id, a);
        return a;
    }

    private static AccountType type(Long id, String code, EAccountCategory category) {
        AccountType t = new AccountType();
        t.setId(id);
        t.setCompanyId(CO);
        t.setCode(code);
        t.setName(code);
        t.setCategory(category);
        return t;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                                       EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(code);
            assertThat(((ApiException) e).getStatus().value()).isEqualTo(404);
        });
    }
}
