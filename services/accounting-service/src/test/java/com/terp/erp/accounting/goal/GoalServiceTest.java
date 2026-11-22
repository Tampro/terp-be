package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.goal.GoalService.ContributionForm;
import com.terp.erp.accounting.goal.GoalService.GoalForm;
import com.terp.erp.accounting.goal.GoalService.MilestoneForm;
import com.terp.erp.accounting.goal.GoalService.TrackingDto;
import com.terp.erp.accounting.ledger.AccountMovement;
import com.terp.erp.accounting.ledger.AccountSum;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.accounting.ledger.JournalService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalServiceTest {
    private static final Long CO = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private final FinancialGoalRepository goals = mock(FinancialGoalRepository.class);
    private final GoalMilestoneRepository milestones = mock(GoalMilestoneRepository.class);
    private final GoalContributionRepository contributions = mock(GoalContributionRepository.class);
    private final GoalCategoryRepository categoryRows = mock(GoalCategoryRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final GoalService service = new GoalService(goals, milestones, contributions,
            new GoalCategoryService(categoryRows), accounts, new AccountMovement(entries, accounts), clock);

    private final Map<Long, FinancialGoal> goalRows = new HashMap<>();
    private final List<GoalContribution> contributionRows = new ArrayList<>();
    private final List<GoalMilestone> milestoneRows = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        TestLogin.as(CO, "GOAL_MANAGE");
        when(goals.findByIdAndDeletedFalse(anyLong()))
                .thenAnswer(i -> Optional.ofNullable(goalRows.get(i.<Long>getArgument(0))).filter(g -> !g.isDeleted()));
        when(goals.save(any())).thenAnswer(i -> {
            FinancialGoal g = i.getArgument(0);
            g.setId(ids.incrementAndGet());
            goalRows.put(g.getId(), g);
            return g;
        });
        when(contributions.findByGoalIdOrderByDateDescIdDesc(anyLong())).thenAnswer(i -> contributionRows.stream()
                .filter(c -> c.getGoalId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(GoalContribution::getDate).thenComparing(GoalContribution::getId)
                        .reversed()).toList());
        when(contributions.save(any())).thenAnswer(i -> {
            GoalContribution c = i.getArgument(0);
            c.setId(ids.incrementAndGet());
            contributionRows.add(c);
            return c;
        });
        when(milestones.findByGoalIdOrderByTargetDateAscIdAsc(anyLong())).thenAnswer(i -> milestoneRows.stream()
                .filter(m -> m.getGoalId().equals(i.getArgument(0))).toList());
        when(milestones.save(any())).thenAnswer(i -> {
            GoalMilestone m = i.getArgument(0);
            m.setId(ids.incrementAndGet());
            milestoneRows.add(m);
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activateOnlyFromDraft() {
        FinancialGoal g = goal(CO, EGoalStatus.DRAFT, "1000", null);
        assertThat(service.activate(g.getId()).status()).isEqualTo(EGoalStatus.ACTIVE);
        assertError(() -> service.activate(g.getId()), EAccountingError.ACC_082);
        assertError(() -> service.update(g.getId(), form(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))),
                EAccountingError.ACC_082);
    }

    @Test
    void targetDateMustBeAfterStart() {
        assertError(() -> service.create(null, form(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1))),
                EAccountingError.ACC_081);
        assertError(() -> service.create(null, form(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))),
                EAccountingError.ACC_081);
        assertThat(service.create(null, form(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).status())
                .isEqualTo(EGoalStatus.DRAFT);
    }

    @Test
    void trackingAheadWithProjectedDateAndPrevious() {
        // 2026-01-01..2027-01-01 = 365 days; today is day 325 → elapsed 89.04 %.
        FinancialGoal g = goal(CO, EGoalStatus.ACTIVE, "1000", null);
        contribution(g, LocalDate.of(2026, 3, 1), "500");
        contribution(g, LocalDate.of(2026, 11, 1), "500");
        TrackingDto t = service.tracking(g.getId());
        assertThat(t.currentAmount()).isEqualByComparingTo("1000");
        assertThat(t.previousAmount()).isEqualByComparingTo("500");
        assertThat(t.lastContribution().amount()).isEqualByComparingTo("500");
        assertThat(t.progressPercent()).isEqualByComparingTo("100");
        assertThat(t.elapsedPercent()).isEqualByComparingTo("89.04");
        assertThat(t.trackingStatus()).isEqualTo(ETrackingStatus.AHEAD);
        assertThat(t.daysRemaining()).isEqualTo(40);
        assertThat(t.projectedCompletionDate()).isEqualTo(TODAY);
    }

    @Test
    void trackingBehindAndCriticalWithLinearProjection() {
        FinancialGoal g = goal(CO, EGoalStatus.ACTIVE, "1000", null);
        contribution(g, LocalDate.of(2026, 6, 1), "700"); // 70 % vs 89.04 % → −19 → BEHIND
        TrackingDto behind = service.tracking(g.getId());
        assertThat(behind.trackingStatus()).isEqualTo(ETrackingStatus.BEHIND);
        // 325 days for 700 → 1000 needs ceil(1000 × 325 / 700) = 465 days from the start.
        assertThat(behind.projectedCompletionDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(465));

        contributionRows.clear();
        contribution(g, LocalDate.of(2026, 6, 1), "500"); // 50 % → −39 → CRITICAL
        assertThat(service.tracking(g.getId()).trackingStatus()).isEqualTo(ETrackingStatus.CRITICAL);
        assertThat(GoalService.trackingStatus(new BigDecimal("80"), new BigDecimal("89.04")))
                .isEqualTo(ETrackingStatus.ON_TRACK);
    }

    @Test
    void milestoneAchievedWhenCurrentReachesItsAmountAndOverdueOtherwise() {
        FinancialGoal g = goal(CO, EGoalStatus.ACTIVE, "1000", null);
        contribution(g, LocalDate.of(2026, 3, 1), "300");
        service.addMilestone(g.getId(), new MilestoneForm("Q1", new BigDecimal("300"), LocalDate.of(2026, 3, 31)));
        service.addMilestone(g.getId(), new MilestoneForm("Q3", new BigDecimal("600"), LocalDate.of(2026, 9, 30)));
        service.addMilestone(g.getId(), new MilestoneForm("Q4", new BigDecimal("900"), LocalDate.of(2026, 12, 31)));
        var list = service.milestones(g.getId());
        assertThat(list).extracting(GoalService.MilestoneDto::status)
                .containsExactly(EMilestoneStatus.ACHIEVED, EMilestoneStatus.PENDING, EMilestoneStatus.PENDING);
        assertThat(list).extracting(GoalService.MilestoneDto::overdue).containsExactly(false, true, false);
    }

    @Test
    void automaticContributionFromCountedLinesOnTheGoalAccount() {
        ChartAccount savings = new ChartAccount();
        savings.setId(7L);
        savings.setCompanyId(CO);
        savings.setNormalBalance(ENormalBalance.DEBIT);
        when(accounts.findById(7L)).thenReturn(Optional.of(savings));
        when(entries.sumsBetween(eq(CO), eq(List.of(7L)), eq(JournalService.COUNTED), eq(LocalDate.of(2026, 1, 1)),
                eq(TODAY))).thenReturn(List.of(new AccountSum(7L, new BigDecimal("900"), new BigDecimal("300"))));
        FinancialGoal g = goal(CO, EGoalStatus.ACTIVE, "1000", 7L);
        contribution(g, LocalDate.of(2026, 2, 1), "100");

        TrackingDto t = service.tracking(g.getId());
        assertThat(t.automaticAmount()).isEqualByComparingTo("600");
        assertThat(t.manualAmount()).isEqualByComparingTo("100");
        assertThat(t.currentAmount()).isEqualByComparingTo("700");
        assertThat(service.contributions(g.getId())).extracting(GoalService.ContributionDto::type)
                .containsExactly(EContributionType.MANUAL, EContributionType.AUTOMATIC);

        savings.setNormalBalance(ENormalBalance.CREDIT); // credit-normal: credit − debit
        assertThat(service.tracking(g.getId()).automaticAmount()).isEqualByComparingTo("-600");
    }

    @Test
    void contributionOnlyOnActiveAndReachingTargetCompletes() {
        FinancialGoal draft = goal(CO, EGoalStatus.DRAFT, "1000", null);
        assertError(() -> service.addContribution(draft.getId(), new ContributionForm(null, BigDecimal.TEN, null)),
                EAccountingError.ACC_083);
        FinancialGoal g = goal(CO, EGoalStatus.ACTIVE, "1000", null);
        service.addContribution(g.getId(), new ContributionForm(null, new BigDecimal("400"), null));
        assertThat(g.getStatus()).isEqualTo(EGoalStatus.ACTIVE);
        service.addContribution(g.getId(), new ContributionForm(null, new BigDecimal("600"), "rest"));
        assertThat(g.getStatus()).isEqualTo(EGoalStatus.COMPLETED);
        assertThat(g.getCompletedAt()).isNotNull();
    }

    @Test
    void anotherCompanysGoalIsNotFound() {
        FinancialGoal other = goal(99L, EGoalStatus.ACTIVE, "1000", null);
        assertError(() -> service.get(other.getId()), EAccountingError.ACC_080);
        assertError(() -> service.tracking(other.getId()), EAccountingError.ACC_080);
        assertError(() -> service.addMilestone(other.getId(),
                new MilestoneForm("x", BigDecimal.ONE, TODAY)), EAccountingError.ACC_080);
        assertError(() -> service.delete(other.getId()), EAccountingError.ACC_080);
        assertThatThrownBy(() -> service.complete(other.getId()))
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    private GoalForm form(LocalDate start, LocalDate target) {
        return new GoalForm("Fund", null, EGoalType.SAVINGS, null, new BigDecimal("1000"), start, target, null, null);
    }

    private FinancialGoal goal(Long company, EGoalStatus status, String target, Long accountId) {
        FinancialGoal g = new FinancialGoal();
        g.setId(ids.incrementAndGet());
        g.setCompanyId(company);
        g.setName("G");
        g.setType(EGoalType.SAVINGS);
        g.setPriority(EGoalPriority.MEDIUM);
        g.setTargetAmount(new BigDecimal(target));
        g.setStartDate(LocalDate.of(2026, 1, 1));
        g.setTargetDate(LocalDate.of(2027, 1, 1));
        g.setAccountId(accountId);
        g.setStatus(status);
        goalRows.put(g.getId(), g);
        return g;
    }

    private void contribution(FinancialGoal g, LocalDate date, String amount) {
        GoalContribution c = new GoalContribution();
        c.setId(ids.incrementAndGet());
        c.setGoalId(g.getId());
        c.setDate(date);
        c.setAmount(new BigDecimal(amount));
        contributionRows.add(c);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
