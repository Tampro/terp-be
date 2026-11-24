package com.terp.erp.accounting.goal;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ENormalBalance;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMovement;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Financial goals (manual §14, FR-GOAL-001/002): DRAFT (edit, activate, delete) → ACTIVE (contributions, complete,
 * delete) → COMPLETED. Current amount = manual contributions + the automatic one computed from the goal's chart
 * account; milestone status and tracking are computed on read, never stored.
 */
@Service
@RequiredArgsConstructor
public class GoalService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinancialGoalRepository goals;
    private final GoalMilestoneRepository milestones;
    private final GoalContributionRepository contributions;
    private final GoalCategoryService categories;
    private final ChartAccountRepository accounts;
    private final AccountMovement movement;
    private final Clock clock;

    public record GoalForm(
            @Schema(example = "Emergency fund", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "1") Long categoryId,
            @Schema(example = "SAVINGS", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EGoalType type,
            @Schema(description = "null = MEDIUM", example = "HIGH") EGoalPriority priority,
            @Schema(example = "100000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal targetAmount,
            @Schema(example = "2026-01-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate startDate,
            @Schema(description = "After startDate", example = "2026-12-31",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate targetDate,
            @Schema(description = "Chart account whose posted movement counts as an automatic contribution",
                    example = "12") Long accountId,
            @Schema(example = "Six months of operating costs") @Size(max = 1000) String description) {
    }

    public record GoalDto(String id, String companyId, String categoryId, String name, EGoalType type,
                          EGoalPriority priority, BigDecimal targetAmount, LocalDate startDate, LocalDate targetDate,
                          String accountId, String description, EGoalStatus status, Instant completedAt,
                          Instant createdDate, Instant updatedDate) {
    }

    public record MilestoneForm(
            @Schema(example = "First quarter", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "25000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal targetAmount,
            @Schema(example = "2026-03-31", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate targetDate) {
    }

    public record MilestoneDto(String id, String goalId, String name, BigDecimal targetAmount, LocalDate targetDate,
                               EMilestoneStatus status, boolean overdue) {
    }

    public record ContributionForm(
            @Schema(description = "null = today (UTC)", example = "2026-11-22") LocalDate date,
            @Schema(example = "5000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(example = "November transfer") @Size(max = 500) String notes) {
    }

    /** id is null for the AUTOMATIC row (computed, not stored). */
    public record ContributionDto(String id, String goalId, LocalDate date, BigDecimal amount, String notes,
                                  EContributionType type) {
    }

    public record TrackingDto(String goalId, EGoalStatus goalStatus, BigDecimal targetAmount,
                              BigDecimal manualAmount, BigDecimal automaticAmount, BigDecimal currentAmount,
                              BigDecimal previousAmount, ContributionDto lastContribution,
                              BigDecimal progressPercent, BigDecimal elapsedPercent, long daysRemaining,
                              LocalDate projectedCompletionDate, ETrackingStatus trackingStatus,
                              List<MilestoneDto> milestones) {
    }

    // ---- goals ----

    @Transactional(readOnly = true)
    public Page<GoalDto> search(Long requestedCompanyId, Long categoryId, EGoalStatus status, EGoalType type,
                                Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return goals.search(companyId, categoryId, status, type, pageable).map(GoalService::dto);
    }

    /** Count per status with the list's other filters; every status is present (0 when none). */
    @Transactional(readOnly = true)
    public java.util.Map<EGoalStatus, Long> statusCounts(Long requestedCompanyId, Long categoryId, EGoalType type) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        java.util.Map<EGoalStatus, Long> counts = new java.util.EnumMap<>(EGoalStatus.class);
        for (EGoalStatus s : EGoalStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] r : goals.countByStatus(companyId, categoryId, type)) {
            counts.put((EGoalStatus) r[0], ((Number) r[1]).longValue());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public GoalDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public GoalDto create(Long requestedCompanyId, GoalForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        FinancialGoal g = new FinancialGoal();
        g.setCompanyId(companyId);
        apply(g, form);
        return dto(goals.save(g));
    }

    /** Full replacement; DRAFT only (ACC_082). */
    @Transactional
    public GoalDto update(Long id, GoalForm form) {
        FinancialGoal g = requireStatus(id, EGoalStatus.DRAFT, EAccountingError.ACC_082);
        apply(g, form);
        return dto(g);
    }

    @Transactional
    public GoalDto activate(Long id) {
        FinancialGoal g = requireStatus(id, EGoalStatus.DRAFT, EAccountingError.ACC_082);
        g.setStatus(EGoalStatus.ACTIVE);
        return dto(g);
    }

    /** ACTIVE → COMPLETED whatever the progress: the manager's call (a manual contribution also completes it). */
    @Transactional
    public GoalDto complete(Long id) {
        FinancialGoal g = requireStatus(id, EGoalStatus.ACTIVE, EAccountingError.ACC_083);
        markCompleted(g);
        return dto(g);
    }

    /** Soft delete in any status (manual: DRAFT and ACTIVE goals can be deleted; COMPLETED is not mentioned). */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    // ---- milestones ----

    @Transactional(readOnly = true)
    public List<MilestoneDto> milestones(Long goalId) {
        FinancialGoal g = require(goalId);
        return milestoneDtos(g, currentAmount(g, manualAmount(g.getId())));
    }

    @Transactional
    public MilestoneDto addMilestone(Long goalId, MilestoneForm form) {
        FinancialGoal g = require(goalId);
        GoalMilestone m = new GoalMilestone();
        m.setGoalId(g.getId());
        applyMilestone(m, form);
        m = milestones.save(m);
        return milestoneDto(m, currentAmount(g, manualAmount(g.getId())));
    }

    @Transactional
    public MilestoneDto updateMilestone(Long goalId, Long milestoneId, MilestoneForm form) {
        FinancialGoal g = require(goalId);
        GoalMilestone m = requireMilestone(g, milestoneId);
        applyMilestone(m, form);
        return milestoneDto(m, currentAmount(g, manualAmount(g.getId())));
    }

    @Transactional
    public void deleteMilestone(Long goalId, Long milestoneId) {
        FinancialGoal g = require(goalId);
        milestones.delete(requireMilestone(g, milestoneId));
    }

    // ---- contributions ----

    /** Manual rows newest first, then the AUTOMATIC row (dated today) when the goal has an account. */
    @Transactional(readOnly = true)
    public List<ContributionDto> contributions(Long goalId) {
        FinancialGoal g = require(goalId);
        List<ContributionDto> result = new ArrayList<>(contributions.findByGoalIdOrderByDateDescIdDesc(g.getId())
                .stream().map(GoalService::contributionDto).toList());
        if (g.getAccountId() != null) {
            result.add(new ContributionDto(null, g.getId().toString(), LocalDate.now(clock), automaticAmount(g),
                    null, EContributionType.AUTOMATIC));
        }
        return result;
    }

    /** ACTIVE only (ACC_083). Reaching the target completes the goal. */
    @Transactional
    public ContributionDto addContribution(Long goalId, ContributionForm form) {
        FinancialGoal g = requireStatus(goalId, EGoalStatus.ACTIVE, EAccountingError.ACC_083);
        GoalContribution c = new GoalContribution();
        c.setGoalId(g.getId());
        c.setDate(form.date() == null ? LocalDate.now(clock) : form.date());
        c.setAmount(form.amount());
        c.setNotes(blankToNull(form.notes()));
        c = contributions.save(c);
        if (currentAmount(g, manualAmount(g.getId())).compareTo(g.getTargetAmount()) >= 0) {
            markCompleted(g);
        }
        return contributionDto(c);
    }

    @Transactional
    public void deleteContribution(Long goalId, Long contributionId) {
        FinancialGoal g = requireStatus(goalId, EGoalStatus.ACTIVE, EAccountingError.ACC_083);
        GoalContribution c = contributions.findById(contributionId)
                .filter(x -> Objects.equals(x.getGoalId(), g.getId()))
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_085));
        contributions.delete(c);
    }

    // ---- tracking ----

    @Transactional(readOnly = true)
    public TrackingDto tracking(Long goalId) {
        FinancialGoal g = require(goalId);
        LocalDate today = LocalDate.now(clock);
        List<GoalContribution> manual = contributions.findByGoalIdOrderByDateDescIdDesc(g.getId());
        BigDecimal manualSum = manual.stream().map(GoalContribution::getAmount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal automatic = automaticAmount(g);
        BigDecimal current = manualSum.add(automatic);
        GoalContribution last = manual.isEmpty() ? null : manual.get(0);
        BigDecimal previous = last == null ? current : current.subtract(last.getAmount());

        BigDecimal progress = percent(current, g.getTargetAmount());
        long totalDays = ChronoUnit.DAYS.between(g.getStartDate(), g.getTargetDate());
        long elapsedDays = Math.max(0, Math.min(totalDays, ChronoUnit.DAYS.between(g.getStartDate(), today)));
        BigDecimal elapsed = percent(BigDecimal.valueOf(elapsedDays), BigDecimal.valueOf(totalDays));
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(today, g.getTargetDate()));

        return new TrackingDto(g.getId().toString(), g.getStatus(), g.getTargetAmount(), manualSum, automatic,
                current, previous, last == null ? null : contributionDto(last), progress, elapsed, daysRemaining,
                projected(g, current, today), trackingStatus(progress, elapsed), milestoneDtos(g, current));
    }

    /** TODO(assumption): thresholds ±10 / −25 points of progress % against elapsed-time %. */
    static ETrackingStatus trackingStatus(BigDecimal progressPercent, BigDecimal elapsedPercent) {
        BigDecimal diff = progressPercent.subtract(elapsedPercent);
        if (diff.compareTo(BigDecimal.TEN) >= 0) {
            return ETrackingStatus.AHEAD;
        }
        if (diff.compareTo(BigDecimal.valueOf(-10)) >= 0) {
            return ETrackingStatus.ON_TRACK;
        }
        if (diff.compareTo(BigDecimal.valueOf(-25)) >= 0) {
            return ETrackingStatus.BEHIND;
        }
        return ETrackingStatus.CRITICAL;
    }

    /**
     * Linear: the pace so far (current / days since start) carried on until the target. Null before any progress
     * or on the start day; today once the target is reached.
     */
    private static LocalDate projected(FinancialGoal g, BigDecimal current, LocalDate today) {
        if (current.compareTo(g.getTargetAmount()) >= 0) {
            return today;
        }
        long elapsed = ChronoUnit.DAYS.between(g.getStartDate(), today);
        if (elapsed <= 0 || current.signum() <= 0) {
            return null;
        }
        BigDecimal days = g.getTargetAmount().multiply(BigDecimal.valueOf(elapsed))
                .divide(current, 0, RoundingMode.CEILING);
        return g.getStartDate().plusDays(days.longValueExact());
    }

    // ---- helpers ----

    FinancialGoal require(Long id) {
        FinancialGoal g = goals.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_080));
        TenantGuard.assertSameCompany(g.getCompanyId(), EAccountingError.ACC_080);
        return g;
    }

    private FinancialGoal requireStatus(Long id, EGoalStatus status, EAccountingError error) {
        FinancialGoal g = require(id);
        if (g.getStatus() != status) {
            throw ApiException.badRequest(error);
        }
        return g;
    }

    private GoalMilestone requireMilestone(FinancialGoal g, Long milestoneId) {
        return milestones.findById(milestoneId).filter(m -> Objects.equals(m.getGoalId(), g.getId()))
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_084));
    }

    private void markCompleted(FinancialGoal g) {
        g.setStatus(EGoalStatus.COMPLETED);
        g.setCompletedAt(Instant.now(clock));
    }

    private BigDecimal manualAmount(Long goalId) {
        return contributions.findByGoalIdOrderByDateDescIdDesc(goalId).stream().map(GoalContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal currentAmount(FinancialGoal g, BigDecimal manual) {
        return manual.add(automaticAmount(g));
    }

    /**
     * TODO(assumption): automatic contribution = net movement of the goal's account on its normal side from counted
     * journal lines dated startDate..today (debit-normal: debit − credit; credit-normal: credit − debit).
     */
    BigDecimal automaticAmount(FinancialGoal g) {
        if (g.getAccountId() == null) {
            return BigDecimal.ZERO;
        }
        ChartAccount a = accounts.findById(g.getAccountId()).orElse(null);
        if (a == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = movement.debitMinusCredit(g.getCompanyId(), List.of(a.getId()), g.getStartDate(),
                LocalDate.now(clock));
        return a.getNormalBalance() == ENormalBalance.CREDIT ? net.negate() : net;
    }

    private List<MilestoneDto> milestoneDtos(FinancialGoal g, BigDecimal current) {
        return milestones.findByGoalIdOrderByTargetDateAscIdAsc(g.getId()).stream()
                .map(m -> milestoneDto(m, current)).toList();
    }

    private MilestoneDto milestoneDto(GoalMilestone m, BigDecimal current) {
        EMilestoneStatus status = current.compareTo(m.getTargetAmount()) >= 0 ? EMilestoneStatus.ACHIEVED
                : EMilestoneStatus.PENDING;
        boolean overdue = status == EMilestoneStatus.PENDING && m.getTargetDate().isBefore(LocalDate.now(clock));
        return new MilestoneDto(m.getId() == null ? null : m.getId().toString(), m.getGoalId().toString(),
                m.getName(), m.getTargetAmount(), m.getTargetDate(), status, overdue);
    }

    private static void applyMilestone(GoalMilestone m, MilestoneForm f) {
        m.setName(f.name().trim());
        m.setTargetAmount(f.targetAmount());
        m.setTargetDate(f.targetDate());
    }

    private void apply(FinancialGoal g, GoalForm f) {
        if (!f.targetDate().isAfter(f.startDate())) {
            throw ApiException.badRequest(EAccountingError.ACC_081);
        }
        if (f.categoryId() != null) {
            GoalCategory c = categories.require(f.categoryId());
            if (!Objects.equals(c.getCompanyId(), g.getCompanyId())) {
                throw ApiException.notFound(EAccountingError.ACC_078);
            }
        }
        if (f.accountId() != null && accounts.findById(f.accountId())
                .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), g.getCompanyId())).isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_020, f.accountId());
        }
        g.setName(f.name().trim());
        g.setCategoryId(f.categoryId());
        g.setType(f.type());
        g.setPriority(f.priority() == null ? EGoalPriority.MEDIUM : f.priority());
        g.setTargetAmount(f.targetAmount());
        g.setStartDate(f.startDate());
        g.setTargetDate(f.targetDate());
        g.setAccountId(f.accountId());
        g.setDescription(blankToNull(f.description()));
    }

    static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static GoalDto dto(FinancialGoal g) {
        return new GoalDto(g.getId() == null ? null : g.getId().toString(), g.getCompanyId().toString(),
                g.getCategoryId() == null ? null : g.getCategoryId().toString(), g.getName(), g.getType(),
                g.getPriority(), g.getTargetAmount(), g.getStartDate(), g.getTargetDate(),
                g.getAccountId() == null ? null : g.getAccountId().toString(), g.getDescription(), g.getStatus(),
                g.getCompletedAt(), g.getCreatedDate(), g.getUpdatedDate());
    }

    private static ContributionDto contributionDto(GoalContribution c) {
        return new ContributionDto(c.getId() == null ? null : c.getId().toString(), c.getGoalId().toString(),
                c.getDate(), c.getAmount(), c.getNotes(), EContributionType.MANUAL);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
