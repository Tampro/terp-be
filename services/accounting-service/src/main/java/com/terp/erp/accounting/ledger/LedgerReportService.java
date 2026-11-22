package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.AccountType;
import com.terp.erp.accounting.coa.AccountTypeRepository;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Double-entry reports (ERP manual 13, FR-DE-004/006/008), computed from journal lines whose entry is POSTED or
 * REVERSED: a reversed entry and its reversal both count, so they cancel. DRAFT entries never count.
 * <p>
 * Internally every amount is "debit-net" (debit - credit); an account's opening balance (stored on its normal side)
 * is converted with {@link #signed}. Reports show balances on the account's normal side.
 */
@Service
@RequiredArgsConstructor
public class LedgerReportService {
    public static final int MAX_DAYS = 366;
    static final String YEAR_END_CLOSE = "YEAR_END_CLOSE";

    private final JournalEntryRepository entries;
    private final ChartAccountRepository accounts;
    private final AccountTypeRepository types;
    private final SystemAccountMappingRepository mappings;
    private final Clock clock;

    public record TrialBalanceRow(String accountId, String code, String name, EAccountCategory category,
                                  BigDecimal openingDebit, BigDecimal openingCredit, BigDecimal periodDebit,
                                  BigDecimal periodCredit, BigDecimal closingDebit, BigDecimal closingCredit) {
    }

    /** balanced = period debit = period credit and closing debit = closing credit; difference = closing D - C. */
    public record TrialBalance(LocalDate from, LocalDate to, List<TrialBalanceRow> rows, TrialBalanceRow totals,
                               boolean balanced, BigDecimal difference) {
    }

    public record AmountLine(String accountId, String code, String name, BigDecimal amount) {
    }

    /** Accounts of one account type (e.g. COST "Chi phí sản xuất, kinh doanh"). */
    public record AmountGroup(String typeCode, String typeName, List<AmountLine> lines, BigDecimal total) {
    }

    public record ProfitLoss(LocalDate from, LocalDate to, List<AmountGroup> revenue, BigDecimal totalRevenue,
                             List<AmountGroup> expenses, BigDecimal totalExpenses, BigDecimal netProfit) {
    }

    public record ExpenseReport(LocalDate from, LocalDate to, List<AmountGroup> categories, BigDecimal total) {
    }

    public enum ECashFlowActivity { OPERATING, INVESTING, FINANCING }

    public record CashFlowLine(String accountId, String code, String name, BigDecimal inflow, BigDecimal outflow,
                               BigDecimal net) {
    }

    public record CashFlowSection(ECashFlowActivity activity, List<CashFlowLine> lines, BigDecimal inflow,
                                  BigDecimal outflow, BigDecimal net) {
    }

    public record CashFlow(LocalDate from, LocalDate to, List<String> cashAccountCodes, BigDecimal openingCash,
                           List<CashFlowSection> sections, BigDecimal netChange, BigDecimal closingCash) {
    }

    /** balance = running balance on the account's normal side; counterpartCodes only in the account statement. */
    public record LedgerRow(String entryId, String number, LocalDate date, String reference, String description,
                            String sourceType, String sourceId, BigDecimal debit, BigDecimal credit,
                            BigDecimal balance, List<String> counterpartCodes) {
    }

    public record GeneralLedger(String accountId, String code, String name, ENormalBalance normalBalance,
                                LocalDate from, LocalDate to, BigDecimal openingBalance, BigDecimal totalDebit,
                                BigDecimal totalCredit, BigDecimal closingBalance, List<LedgerRow> lines) {
    }

    public record AccountBalanceRow(String accountId, String code, String name, EAccountCategory category,
                                    ENormalBalance normalBalance, BigDecimal balance) {
    }

    public record AccountBalances(LocalDate asOf, boolean hideZero, List<AccountBalanceRow> rows) {
    }

    /** The company's accounts (by id, ordered by code) and each account's category. */
    record Chart(Map<Long, ChartAccount> accounts, Map<Long, EAccountCategory> category, Map<Long, AccountType> type) {
        EAccountCategory categoryOf(ChartAccount a) {
            return category.get(a.getId());
        }
    }

    // ---------- reports ----------

    @Transactional(readOnly = true)
    public TrialBalance trialBalance(Long requestedCompanyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkPeriod(from, to);
        Chart chart = chart(company);
        Map<Long, BigDecimal> opening = netBefore(company, from, chart);
        Map<Long, BigDecimal[]> period = new HashMap<>();
        for (LedgerLine l : entries.lines(company, JournalService.COUNTED, from, to)) {
            BigDecimal[] dc = period.computeIfAbsent(l.accountId(), k -> new BigDecimal[]{BigDecimal.ZERO,
                    BigDecimal.ZERO});
            dc[0] = dc[0].add(l.debit());
            dc[1] = dc[1].add(l.credit());
        }
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal[] t = zeros(6);
        for (ChartAccount a : chart.accounts().values()) {
            BigDecimal open = opening.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal[] dc = period.getOrDefault(a.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal close = open.add(dc[0]).subtract(dc[1]);
            if (open.signum() == 0 && dc[0].signum() == 0 && dc[1].signum() == 0) {
                continue;
            }
            BigDecimal[] v = {pos(open), pos(open.negate()), dc[0], dc[1], pos(close), pos(close.negate())};
            for (int i = 0; i < 6; i++) {
                t[i] = t[i].add(v[i]);
            }
            rows.add(new TrialBalanceRow(a.getId().toString(), a.getCode(), a.getName(), chart.categoryOf(a),
                    v[0], v[1], v[2], v[3], v[4], v[5]));
        }
        TrialBalanceRow totals = new TrialBalanceRow(null, null, "Total", null, t[0], t[1], t[2], t[3], t[4], t[5]);
        boolean balanced = t[2].compareTo(t[3]) == 0 && t[4].compareTo(t[5]) == 0;
        return new TrialBalance(from, to, rows, totals, balanced, t[4].subtract(t[5]));
    }

    /**
     * Revenue (credit - debit) and expense (debit - credit) movements in the period, grouped by account type. The
     * year-end closing entry is left out, otherwise a closed year would show zero.
     */
    @Transactional(readOnly = true)
    public ProfitLoss profitLoss(Long requestedCompanyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkPeriod(from, to);
        Chart chart = chart(company);
        Map<Long, BigDecimal> moves = operatingMoves(company, from, to);
        List<AmountGroup> revenue = groups(chart, moves, EAccountCategory.REVENUE);
        List<AmountGroup> expenses = groups(chart, moves, EAccountCategory.EXPENSES);
        BigDecimal r = revenue.stream().map(AmountGroup::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal x = expenses.stream().map(AmountGroup::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProfitLoss(from, to, revenue, r, expenses, x, r.subtract(x));
    }

    /**
     * Expense accounts and their categories in the period. TODO(assumption): "expense category" = account type of
     * category EXPENSES (COST, OE ...); revenue / expense voucher categories each post to one GL account, so the
     * per-account totals already cover them.
     */
    @Transactional(readOnly = true)
    public ExpenseReport expenseReport(Long requestedCompanyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkPeriod(from, to);
        Chart chart = chart(company);
        List<AmountGroup> groups = groups(chart, operatingMoves(company, from, to), EAccountCategory.EXPENSES);
        return new ExpenseReport(from, to, groups,
                groups.stream().map(AmountGroup::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Direct method: each entry touching a cash account is split by its non-cash lines; a line's (credit - debit) is
     * cash in (+) or out (-) for the activity of that line's account. Entries only between cash accounts net to zero
     * and are left out. TODO(assumption): VAS rule of thumb — 21x/22x/24x (fixed assets, investments) Investing,
     * 34x/41x (borrowings, owner capital) Financing, everything else Operating; confirm with the chief accountant.
     */
    @Transactional(readOnly = true)
    public CashFlow cashFlow(Long requestedCompanyId, LocalDate from, LocalDate to) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        checkPeriod(from, to);
        Chart chart = chart(company);
        Set<Long> cash = cashAccounts(company, chart);
        Map<Long, BigDecimal> opening = netBefore(company, from, chart);
        BigDecimal openingCash = cash.stream().map(id -> opening.getOrDefault(id, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, List<LedgerLine>> byEntry = new LinkedHashMap<>();
        for (LedgerLine l : entries.lines(company, JournalService.COUNTED, from, to)) {
            byEntry.computeIfAbsent(l.entryId(), k -> new ArrayList<>()).add(l);
        }
        Map<ECashFlowActivity, Map<Long, BigDecimal[]>> acc = new EnumMap<>(ECashFlowActivity.class);
        for (ECashFlowActivity a : ECashFlowActivity.values()) {
            acc.put(a, new LinkedHashMap<>());
        }
        for (List<LedgerLine> lines : byEntry.values()) {
            if (lines.stream().noneMatch(l -> cash.contains(l.accountId()))) {
                continue;
            }
            for (LedgerLine l : lines) {
                if (cash.contains(l.accountId())) {
                    continue;
                }
                ChartAccount a = chart.accounts().get(l.accountId());
                ECashFlowActivity activity = activity(a == null ? "" : a.getCode());
                BigDecimal[] io = acc.get(activity).computeIfAbsent(l.accountId(), k -> zeros(2));
                // A credit on the counterpart is money in; a debit is money out.
                io[0] = io[0].add(l.credit());
                io[1] = io[1].add(l.debit());
            }
        }
        List<CashFlowSection> sections = new ArrayList<>();
        BigDecimal netChange = BigDecimal.ZERO;
        for (ECashFlowActivity activity : ECashFlowActivity.values()) {
            List<CashFlowLine> lines = new ArrayList<>();
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal[]> e : acc.get(activity).entrySet()) {
                ChartAccount a = chart.accounts().get(e.getKey());
                BigDecimal[] io = e.getValue();
                lines.add(new CashFlowLine(e.getKey().toString(), a == null ? null : a.getCode(),
                        a == null ? null : a.getName(), io[0], io[1], io[0].subtract(io[1])));
                in = in.add(io[0]);
                out = out.add(io[1]);
            }
            lines.sort(Comparator.comparing(l -> l.code() == null ? "" : l.code()));
            sections.add(new CashFlowSection(activity, lines, in, out, in.subtract(out)));
            netChange = netChange.add(in.subtract(out));
        }
        List<String> codes = cash.stream().map(id -> chart.accounts().get(id)).filter(java.util.Objects::nonNull)
                .map(ChartAccount::getCode).sorted().toList();
        return new CashFlow(from, to, codes, openingCash, sections, netChange, openingCash.add(netChange));
    }

    @Transactional(readOnly = true)
    public GeneralLedger generalLedger(Long accountId, LocalDate from, LocalDate to) {
        return ledger(accountId, from, to, false);
    }

    /** The general ledger of one account plus, per line, the other accounts of the entry and its source. */
    @Transactional(readOnly = true)
    public GeneralLedger accountStatement(Long accountId, LocalDate from, LocalDate to) {
        return ledger(accountId, from, to, true);
    }

    @Transactional(readOnly = true)
    public AccountBalances accountBalances(Long requestedCompanyId, LocalDate asOf, boolean hideZero) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
        Chart chart = chart(company);
        Map<Long, BigDecimal> net = netAsOf(company, date, chart);
        List<AccountBalanceRow> rows = new ArrayList<>();
        for (ChartAccount a : chart.accounts().values()) {
            BigDecimal balance = signed(a, net.getOrDefault(a.getId(), BigDecimal.ZERO));
            if (hideZero && balance.signum() == 0) {
                continue;
            }
            rows.add(new AccountBalanceRow(a.getId().toString(), a.getCode(), a.getName(), chart.categoryOf(a),
                    a.getNormalBalance(), balance));
        }
        return new AccountBalances(date, hideZero, rows);
    }

    // ---------- shared with balance sheet and year-end close ----------

    Chart chart(Long company) {
        Map<Long, AccountType> typeById = new HashMap<>();
        types.findByCompanyId(company).forEach(t -> typeById.put(t.getId(), t));
        Map<Long, ChartAccount> byId = new LinkedHashMap<>();
        Map<Long, EAccountCategory> category = new HashMap<>();
        Map<Long, AccountType> type = new HashMap<>();
        for (ChartAccount a : accounts.findByCompanyIdOrderByCode(company)) {
            byId.put(a.getId(), a);
            AccountType t = typeById.get(a.getAccountTypeId());
            if (t != null) {
                category.put(a.getId(), t.getCategory());
                type.put(a.getId(), t);
            }
        }
        return new Chart(byId, category, type);
    }

    /** Debit-net balance per account at the end of {@code date}: opening balance + counted lines up to date. */
    Map<Long, BigDecimal> netAsOf(Long company, LocalDate date, Chart chart) {
        return netBefore(company, date.plusDays(1), chart);
    }

    /** Debit-net balance per account before {@code date}. */
    Map<Long, BigDecimal> netBefore(Long company, LocalDate date, Chart chart) {
        Map<Long, BigDecimal> net = new HashMap<>();
        for (ChartAccount a : chart.accounts().values()) {
            if (a.getOpeningBalance() != null && a.getOpeningBalance().signum() != 0) {
                net.put(a.getId(), signed(a, a.getOpeningBalance()));
            }
        }
        for (AccountSum s : entries.sumsBefore(company, JournalService.COUNTED, date)) {
            net.merge(s.accountId(), JournalService.orZero(s.debit()).subtract(JournalService.orZero(s.credit())),
                    BigDecimal::add);
        }
        return net;
    }

    /** Converts between debit-net and the account's normal side (the same flip both ways). */
    static BigDecimal signed(ChartAccount a, BigDecimal amount) {
        return a.getNormalBalance() == ENormalBalance.CREDIT ? amount.negate() : amount;
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EAccountingError.ACC_065, MAX_DAYS);
        }
    }

    static ECashFlowActivity activity(String code) {
        if (code.startsWith("21") || code.startsWith("22") || code.startsWith("24")) {
            return ECashFlowActivity.INVESTING;
        }
        if (code.startsWith("34") || code.startsWith("41")) {
            return ECashFlowActivity.FINANCING;
        }
        return ECashFlowActivity.OPERATING;
    }

    // ---------- helpers ----------

    private GeneralLedger ledger(Long accountId, LocalDate from, LocalDate to, boolean counterparts) {
        checkPeriod(from, to);
        ChartAccount account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_010));
        TenantGuard.assertSameCompany(account.getCompanyId(), EAccountingError.ACC_010);
        Long company = account.getCompanyId();
        BigDecimal opening = signed(account, JournalService.orZero(account.getOpeningBalance()));
        for (AccountSum s : entries.sumsBefore(company, JournalService.COUNTED, from)) {
            if (accountId.equals(s.accountId())) {
                opening = opening.add(JournalService.orZero(s.debit()).subtract(JournalService.orZero(s.credit())));
            }
        }
        List<LedgerLine> lines = entries.accountLines(company, accountId, JournalService.COUNTED, from, to);
        Map<Long, List<String>> others = counterparts ? counterparts(lines, accountId) : Map.of();
        BigDecimal running = opening;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<LedgerRow> rows = new ArrayList<>();
        for (LedgerLine l : lines) {
            running = running.add(l.debit()).subtract(l.credit());
            debit = debit.add(l.debit());
            credit = credit.add(l.credit());
            rows.add(new LedgerRow(l.entryId().toString(), l.number(), l.date(), l.reference(),
                    l.lineDescription() == null ? l.description() : l.lineDescription(), l.sourceType(),
                    l.sourceId() == null ? null : l.sourceId().toString(), l.debit(), l.credit(),
                    signed(account, running), counterparts ? others.getOrDefault(l.entryId(), List.of()) : null));
        }
        return new GeneralLedger(account.getId().toString(), account.getCode(), account.getName(),
                account.getNormalBalance(), from, to, signed(account, opening), debit, credit,
                signed(account, running), rows);
    }

    private Map<Long, List<String>> counterparts(List<LedgerLine> lines, Long accountId) {
        Map<Long, List<String>> out = new HashMap<>();
        List<Long> ids = lines.stream().map(LedgerLine::entryId).distinct().toList();
        if (ids.isEmpty()) {
            return out;
        }
        List<JournalEntry> list = entries.findAllById(ids);
        Set<Long> accountIds = new HashSet<>();
        list.forEach(e -> e.getLines().forEach(l -> accountIds.add(l.getAccountId())));
        Map<Long, String> codes = new HashMap<>();
        accounts.findAllById(accountIds).forEach(a -> codes.put(a.getId(), a.getCode()));
        for (JournalEntry e : list) {
            out.put(e.getId(), e.getLines().stream().map(JournalLine::getAccountId)
                    .filter(id -> !id.equals(accountId)).distinct()
                    .map(id -> codes.getOrDefault(id, id.toString())).toList());
        }
        return out;
    }

    /** Debit-net movement per account in the period, without the year-end closing entry. */
    private Map<Long, BigDecimal> operatingMoves(Long company, LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> moves = new HashMap<>();
        for (LedgerLine l : entries.lines(company, JournalService.COUNTED, from, to)) {
            if (YEAR_END_CLOSE.equals(l.sourceType())) {
                continue;
            }
            moves.merge(l.accountId(), l.debit().subtract(l.credit()), BigDecimal::add);
        }
        return moves;
    }

    /** Groups by account type; revenue shown as credit - debit, expenses as debit - credit. */
    private static List<AmountGroup> groups(Chart chart, Map<Long, BigDecimal> moves, EAccountCategory category) {
        Map<Long, List<AmountLine>> byType = new LinkedHashMap<>();
        Map<Long, AccountType> typeOf = new HashMap<>();
        for (ChartAccount a : chart.accounts().values()) {
            if (chart.categoryOf(a) != category) {
                continue;
            }
            BigDecimal net = moves.getOrDefault(a.getId(), BigDecimal.ZERO);
            if (net.signum() == 0) {
                continue;
            }
            BigDecimal amount = category == EAccountCategory.REVENUE ? net.negate() : net;
            AccountType t = chart.type().get(a.getId());
            typeOf.put(a.getAccountTypeId(), t);
            byType.computeIfAbsent(a.getAccountTypeId(), k -> new ArrayList<>())
                    .add(new AmountLine(a.getId().toString(), a.getCode(), a.getName(), amount));
        }
        List<AmountGroup> out = new ArrayList<>();
        for (Map.Entry<Long, List<AmountLine>> e : byType.entrySet()) {
            AccountType t = typeOf.get(e.getKey());
            out.add(new AmountGroup(t == null ? null : t.getCode(), t == null ? null : t.getName(), e.getValue(),
                    e.getValue().stream().map(AmountLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return out;
    }

    /**
     * Accounts mapped to CASH and BANK, their descendants, and every 111 / 112 / 113 account (VAS "Tiền").
     * TODO(assumption): bank accounts linked to other GL accounts are not added unless they sit under these codes.
     */
    private Set<Long> cashAccounts(Long company, Chart chart) {
        Set<Long> cash = new HashSet<>();
        for (SystemAccountMapping m : mappings.findByCompanyId(company)) {
            if (m.getSystemKey() == ESystemAccount.CASH || m.getSystemKey() == ESystemAccount.BANK) {
                cash.add(m.getAccountId());
            }
        }
        for (ChartAccount a : chart.accounts().values()) {
            String c = a.getCode();
            if (c.startsWith("111") || c.startsWith("112") || c.startsWith("113")) {
                cash.add(a.getId());
            }
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (ChartAccount a : chart.accounts().values()) {
                if (a.getParentId() != null && cash.contains(a.getParentId()) && cash.add(a.getId())) {
                    grew = true;
                }
            }
        }
        return cash;
    }

    private static BigDecimal pos(BigDecimal v) {
        return v.signum() > 0 ? v : BigDecimal.ZERO;
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }
}
