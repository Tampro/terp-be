package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.budget.BudgetPeriodService.PeriodForm;
import com.terp.erp.accounting.budget.BudgetService.AllocationForm;
import com.terp.erp.accounting.budget.BudgetService.MonitoringDto;
import com.terp.erp.accounting.coa.AccountType;
import com.terp.erp.accounting.coa.AccountTypeRepository;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMovement;
import com.terp.erp.accounting.ledger.AccountSum;
import com.terp.erp.accounting.ledger.EJournalStatus;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Budgets over an in-memory journal: sumsBetween is answered by filtering fake lines on company, account, status and
 * date exactly as the JPQL does, so the test checks which dates and statuses the service asks for.
 */
@SuppressWarnings("unchecked")
class BudgetServiceTest {
    private static final Long CO = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private final BudgetPeriodRepository periodRows = mock(BudgetPeriodRepository.class);
    private final BudgetRepository budgetRows = mock(BudgetRepository.class);
    private final BudgetAllocationRepository allocationRows = mock(BudgetAllocationRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final AccountTypeRepository types = mock(AccountTypeRepository.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final BudgetPeriodService periods = new BudgetPeriodService(periodRows, budgetRows, clock);
    private final BudgetService service = new BudgetService(budgetRows, allocationRows, periods, accounts, types,
            new AccountMovement(entries, accounts), clock);

    private final Map<Long, BudgetPeriod> periodMap = new HashMap<>();
    private final Map<Long, Budget> budgetMap = new HashMap<>();
    private final Map<Long, BudgetAllocation> allocationMap = new LinkedHashMap<>();
    private final Map<Long, ChartAccount> chart = new HashMap<>();
    private final List<FakeLine> journal = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    private record FakeLine(Long companyId, Long accountId, LocalDate date, EJournalStatus status, String debit,
                            String credit) {
    }

    @BeforeEach
    void setUp() {
        TestLogin.as(CO, "BUDGET_MANAGE", "BUDGET_APPROVE");
        AccountType expenses = new AccountType();
        expenses.setId(5L);
        expenses.setCategory(EAccountCategory.EXPENSES);
        AccountType assets = new AccountType();
        assets.setId(1L);
        assets.setCategory(EAccountCategory.ASSETS);
        when(types.findById(5L)).thenReturn(Optional.of(expenses));
        when(types.findById(1L)).thenReturn(Optional.of(assets));
        account(40L, "642", 5L, null);
        account(41L, "6421", 5L, 40L);
        account(11L, "111", 1L, null);
        account(42L, "627", 5L, null);
        when(accounts.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(chart.get(i.<Long>getArgument(0))));
        when(accounts.findByCompanyIdOrderByCode(CO)).thenAnswer(i -> new ArrayList<>(chart.values()));

        when(periodRows.findByIdAndDeletedFalse(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(periodMap.get(i.<Long>getArgument(0))));
        when(periodRows.save(any())).thenAnswer(i -> {
            BudgetPeriod p = i.getArgument(0);
            p.setId(ids.incrementAndGet());
            periodMap.put(p.getId(), p);
            return p;
        });
        when(budgetRows.findByIdAndDeletedFalse(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(budgetMap.get(i.<Long>getArgument(0))));
        when(allocationRows.findByBudgetIdOrderByIdAsc(anyLong())).thenAnswer(i -> allocationMap.values().stream()
                .filter(a -> a.getBudgetId().equals(i.getArgument(0))).toList());
        when(allocationRows.findById(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(allocationMap.get(i.<Long>getArgument(0))));
        when(allocationRows.save(any())).thenAnswer(i -> {
            BudgetAllocation a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(ids.incrementAndGet());
            }
            allocationMap.put(a.getId(), a);
            return a;
        });
        when(entries.sumsBetween(any(), any(), any(), any(), any())).thenAnswer(i -> {
            Long company = i.getArgument(0);
            Collection<Long> accountIds = i.getArgument(1);
            Collection<EJournalStatus> statuses = i.getArgument(2);
            LocalDate from = i.getArgument(3);
            LocalDate to = i.getArgument(4);
            Map<Long, BigDecimal[]> sums = new LinkedHashMap<>();
            for (FakeLine l : journal) {
                if (Objects.equals(l.companyId(), company) && accountIds.contains(l.accountId())
                        && statuses.contains(l.status()) && !l.date().isBefore(from) && !l.date().isAfter(to)) {
                    BigDecimal[] s = sums.computeIfAbsent(l.accountId(),
                            k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    s[0] = s[0].add(new BigDecimal(l.debit()));
                    s[1] = s[1].add(new BigDecimal(l.credit()));
                }
            }
            return sums.entrySet().stream().map(e -> new AccountSum(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .toList();
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allocationsOverTheBudgetAmountAreRefused() {
        Budget b = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        String first = service.addAllocation(b.getId(), new AllocationForm(40L, new BigDecimal("700"), null)).id();
        assertError(() -> service.addAllocation(b.getId(), new AllocationForm(42L, new BigDecimal("301"), null)),
                EAccountingError.ACC_094);
        assertThat(service.addAllocation(b.getId(), new AllocationForm(42L, new BigDecimal("300"), null))
                .allocated()).isEqualByComparingTo("300");
        // editing a line counts the others plus its new amount, not its old one
        assertError(() -> service.updateAllocation(b.getId(), Long.valueOf(first),
                new AllocationForm(40L, new BigDecimal("701"), null)), EAccountingError.ACC_094);
        assertThat(service.updateAllocation(b.getId(), Long.valueOf(first),
                new AllocationForm(40L, new BigDecimal("700"), "same")).notes()).isEqualTo("same");
    }

    @Test
    void nonExpenseAccountIsRefused() {
        Budget b = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        assertError(() -> service.addAllocation(b.getId(), new AllocationForm(11L, BigDecimal.TEN, null)),
                EAccountingError.ACC_093);
    }

    @Test
    void spentCountsPostedLinesInThePeriodOnlyWithChildrenAndVarianceSign() {
        BudgetPeriod p = period(CO, EBudgetPeriodStatus.ACTIVE);
        Budget b = budget(CO, p, EBudgetStatus.ACTIVE, "1000");
        allocation(b, 40L, "500");
        allocation(b, 42L, "100");
        line(40L, LocalDate.of(2026, 3, 1), EJournalStatus.POSTED, "300", "0");
        line(41L, LocalDate.of(2026, 4, 1), EJournalStatus.POSTED, "150", "0");      // child of 642
        line(40L, LocalDate.of(2026, 5, 1), EJournalStatus.REVERSED, "80", "0");     // reversed …
        line(40L, LocalDate.of(2026, 5, 2), EJournalStatus.POSTED, "0", "80");       // … and its reversal
        line(40L, LocalDate.of(2026, 6, 1), EJournalStatus.DRAFT, "999", "0");       // draft: ignored
        line(40L, LocalDate.of(2025, 12, 31), EJournalStatus.POSTED, "999", "0");    // before the period
        line(40L, LocalDate.of(2027, 1, 1), EJournalStatus.POSTED, "999", "0");      // after the period
        line(40L, LocalDate.of(2026, 7, 1), EJournalStatus.POSTED, "0", "50");       // credit reduces spent
        line(42L, LocalDate.of(2026, 8, 1), EJournalStatus.POSTED, "150", "0");

        MonitoringDto m = service.monitoring(b.getId());
        var parent = m.lines().get(0);
        var other = m.lines().get(1);
        assertThat(parent.spent()).isEqualByComparingTo("400");          // 300 + 150 + 80 − 80 − 50
        assertThat(parent.remaining()).isEqualByComparingTo("100");
        assertThat(parent.variancePercent()).isEqualByComparingTo("20");
        assertThat(parent.overBudget()).isFalse();
        assertThat(other.spent()).isEqualByComparingTo("150");
        assertThat(other.remaining()).isEqualByComparingTo("-50");
        assertThat(other.variancePercent()).isEqualByComparingTo("-50");
        assertThat(other.overBudget()).isTrue();
        assertThat(m.allocated()).isEqualByComparingTo("600");
        assertThat(m.spent()).isEqualByComparingTo("550");               // no line counted twice
        assertThat(m.variancePercent()).isEqualByComparingTo("8.33");

        when(budgetRows.findByPeriodIdAndDeletedFalseOrderByIdAsc(p.getId())).thenReturn(List.of(b));
        assertThat(service.periodMonitoring(p.getId()).spent()).isEqualByComparingTo("550");
    }

    @Test
    void parentAndChildAccountsCannotBothBeAllocatedInOneBudget() {
        Budget b = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        service.addAllocation(b.getId(), new AllocationForm(40L, new BigDecimal("100"), null));
        assertError(() -> service.addAllocation(b.getId(), new AllocationForm(41L, BigDecimal.TEN, null)),
                EAccountingError.ACC_097);                               // child of an allocated parent

        Budget c = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        service.addAllocation(c.getId(), new AllocationForm(41L, new BigDecimal("100"), null));
        assertError(() -> service.addAllocation(c.getId(), new AllocationForm(40L, BigDecimal.TEN, null)),
                EAccountingError.ACC_097);                               // parent of an allocated child
        assertThat(service.addAllocation(c.getId(), new AllocationForm(42L, BigDecimal.TEN, null)).accountId())
                .isEqualTo("42");                                        // unrelated account is fine
    }

    @Test
    void budgetStatusOrderAndApprovedBy() {
        Budget b = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        assertError(() -> service.activate(b.getId()), EAccountingError.ACC_091);
        assertError(() -> service.close(b.getId()), EAccountingError.ACC_091);
        var approved = service.approve(b.getId());
        assertThat(approved.status()).isEqualTo(EBudgetStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo("5");
        assertThat(approved.approvedAt()).isNotNull();
        assertError(() -> service.approve(b.getId()), EAccountingError.ACC_091);
        assertError(() -> service.delete(b.getId()), EAccountingError.ACC_091);
        assertThat(service.activate(b.getId()).status()).isEqualTo(EBudgetStatus.ACTIVE);
        assertThat(service.close(b.getId()).status()).isEqualTo(EBudgetStatus.CLOSED);
    }

    @Test
    void closePeriodOnlyAfterItsEndAndThenReadOnly() {
        BudgetPeriod current = period(CO, EBudgetPeriodStatus.ACTIVE); // ends 2026-12-31, after today
        assertError(() -> periods.close(current.getId()), EAccountingError.ACC_088);

        BudgetPeriod past = period(CO, EBudgetPeriodStatus.ACTIVE);
        past.setStartDate(LocalDate.of(2025, 1, 1));
        past.setEndDate(LocalDate.of(2025, 12, 31));
        Budget b = budget(CO, past, EBudgetStatus.DRAFT, "1000");
        assertThat(periods.close(past.getId()).status()).isEqualTo(EBudgetPeriodStatus.CLOSED);
        assertError(() -> periods.close(past.getId()), EAccountingError.ACC_089);
        assertError(() -> service.approve(b.getId()), EAccountingError.ACC_089);
        assertError(() -> service.addAllocation(b.getId(), new AllocationForm(40L, BigDecimal.ONE, null)),
                EAccountingError.ACC_089);
    }

    @Test
    void periodDatesValidated() {
        assertError(() -> periods.create(null, new PeriodForm("P", 2026, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 1))), EAccountingError.ACC_087);
        assertError(() -> periods.create(null, new PeriodForm("P", 2026, LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 2))), EAccountingError.ACC_087);
        assertThat(periods.create(null, new PeriodForm("P", 2026, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31))).status()).isEqualTo(EBudgetPeriodStatus.ACTIVE);
    }

    @Test
    void anotherCompanysBudgetPeriodAndAllocationAreNotFound() {
        BudgetPeriod otherPeriod = period(99L, EBudgetPeriodStatus.ACTIVE);
        Budget other = budget(99L, otherPeriod, EBudgetStatus.DRAFT, "1000");
        assertError(() -> service.get(other.getId()), EAccountingError.ACC_090);
        assertError(() -> service.monitoring(other.getId()), EAccountingError.ACC_090);
        assertError(() -> service.approve(other.getId()), EAccountingError.ACC_090);
        assertError(() -> periods.get(otherPeriod.getId()), EAccountingError.ACC_086);
        assertError(() -> periods.close(otherPeriod.getId()), EAccountingError.ACC_086);

        Budget mine = budget(CO, period(CO, EBudgetPeriodStatus.ACTIVE), EBudgetStatus.DRAFT, "1000");
        BudgetAllocation foreign = allocation(other, 40L, "10");
        assertError(() -> service.deleteAllocation(mine.getId(), foreign.getId()), EAccountingError.ACC_092);
        assertError(() -> service.deleteAllocation(other.getId(), foreign.getId()), EAccountingError.ACC_090);
    }

    private void account(Long id, String code, Long typeId, Long parentId) {
        ChartAccount a = new ChartAccount();
        a.setId(id);
        a.setCompanyId(CO);
        a.setCode(code);
        a.setName(code);
        a.setAccountTypeId(typeId);
        a.setParentId(parentId);
        a.setNormalBalance(ENormalBalance.DEBIT);
        chart.put(id, a);
    }

    private BudgetPeriod period(Long company, EBudgetPeriodStatus status) {
        BudgetPeriod p = new BudgetPeriod();
        p.setId(ids.incrementAndGet());
        p.setCompanyId(company);
        p.setName("FY2026");
        p.setFinancialYear(2026);
        p.setStartDate(LocalDate.of(2026, 1, 1));
        p.setEndDate(LocalDate.of(2026, 12, 31));
        p.setStatus(status);
        periodMap.put(p.getId(), p);
        return p;
    }

    private Budget budget(Long company, BudgetPeriod p, EBudgetStatus status, String amount) {
        Budget b = new Budget();
        b.setId(ids.incrementAndGet());
        b.setCompanyId(company);
        b.setPeriodId(p.getId());
        b.setName("Ops");
        b.setType(EBudgetType.OPERATIONAL);
        b.setAmount(new BigDecimal(amount));
        b.setStatus(status);
        budgetMap.put(b.getId(), b);
        return b;
    }

    private BudgetAllocation allocation(Budget b, Long accountId, String amount) {
        BudgetAllocation a = new BudgetAllocation();
        a.setId(ids.incrementAndGet());
        a.setBudgetId(b.getId());
        a.setAccountId(accountId);
        a.setAllocated(new BigDecimal(amount));
        allocationMap.put(a.getId(), a);
        return a;
    }

    private void line(Long accountId, LocalDate date, EJournalStatus status, String debit, String credit) {
        journal.add(new FakeLine(CO, accountId, date, status, debit, credit));
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
