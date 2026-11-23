package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.coa.AccountTypeRepository;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.EAccountCategory;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMovement;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Budgets (manual §14, FR-BUD-002..004): DRAFT (edit, delete, allocations) → approve → APPROVED → activate →
 * ACTIVE → close → CLOSED; nothing changes once the period is CLOSED (ACC_089). Spent per allocation = counted
 * journal debits − credits on the account and its children dated inside the period, computed on read.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgets;
    private final BudgetAllocationRepository allocations;
    private final BudgetPeriodService periods;
    private final ChartAccountRepository accounts;
    private final AccountTypeRepository accountTypes;
    private final AccountMovement movement;
    private final Clock clock;

    public record BudgetForm(
            @Schema(example = "Operating 2026", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long periodId,
            @Schema(example = "OPERATIONAL", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EBudgetType type,
            @Schema(description = "Total; allocations may not exceed it", example = "500000000",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(example = "Office and selling costs") @Size(max = 1000) String description) {
    }

    public record BudgetDto(String id, String companyId, String periodId, String name, EBudgetType type,
                            BigDecimal amount, String description, EBudgetStatus status, String approvedBy,
                            Instant approvedAt, Instant createdDate, Instant updatedDate) {
    }

    public record AllocationForm(
            @Schema(description = "Expense account (EXPENSES category)", example = "40",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long accountId,
            @Schema(example = "120000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal allocated,
            @Schema(example = "Rent and utilities") @Size(max = 500) String notes) {
    }

    public record AllocationDto(String id, String budgetId, String accountId, String accountCode,
                                String accountName, BigDecimal allocated, BigDecimal spent, BigDecimal remaining,
                                BigDecimal variancePercent, boolean overBudget, String notes) {
    }

    public record MonitoringDto(String budgetId, String name, EBudgetType type, EBudgetStatus status,
                                BigDecimal budgetAmount, BigDecimal allocated, BigDecimal spent,
                                BigDecimal remaining, BigDecimal variancePercent, boolean overBudget,
                                List<AllocationDto> lines) {
    }

    public record PeriodMonitoringDto(String periodId, String name, LocalDate startDate, LocalDate endDate,
                                      EBudgetPeriodStatus status, BigDecimal allocated, BigDecimal spent,
                                      BigDecimal remaining, BigDecimal variancePercent, boolean overBudget,
                                      List<MonitoringDto> budgets) {
    }

    // ---- budgets ----

    @Transactional(readOnly = true)
    public Page<BudgetDto> search(Long requestedCompanyId, Long periodId, EBudgetStatus status, EBudgetType type,
                                  Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return budgets.search(companyId, periodId, status, type, pageable).map(BudgetService::dto);
    }

    @Transactional(readOnly = true)
    public BudgetDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public BudgetDto create(Long requestedCompanyId, BudgetForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        Budget b = new Budget();
        b.setCompanyId(companyId);
        apply(b, form);
        return dto(budgets.save(b));
    }

    /** Full replacement; DRAFT only; the amount cannot fall below the allocations (ACC_094). */
    @Transactional
    public BudgetDto update(Long id, BudgetForm form) {
        Budget b = requireStatus(id, EBudgetStatus.DRAFT);
        apply(b, form);
        BigDecimal allocated = allocatedTotal(b.getId(), null);
        if (allocated.compareTo(b.getAmount()) > 0) {
            throw ApiException.badRequest(EAccountingError.ACC_094, allocated, b.getAmount());
        }
        return dto(b);
    }

    @Transactional
    public void delete(Long id) {
        requireStatus(id, EBudgetStatus.DRAFT).setDeleted(true);
    }

    @Transactional
    public BudgetDto approve(Long id) {
        Budget b = requireStatus(id, EBudgetStatus.DRAFT);
        b.setStatus(EBudgetStatus.APPROVED);
        b.setApprovedBy(CurrentUser.get().userId());
        b.setApprovedAt(Instant.now(clock));
        return dto(b);
    }

    @Transactional
    public BudgetDto activate(Long id) {
        Budget b = requireStatus(id, EBudgetStatus.APPROVED);
        b.setStatus(EBudgetStatus.ACTIVE);
        return dto(b);
    }

    @Transactional
    public BudgetDto close(Long id) {
        Budget b = requireStatus(id, EBudgetStatus.ACTIVE);
        b.setStatus(EBudgetStatus.CLOSED);
        return dto(b);
    }

    // ---- allocations ----

    @Transactional(readOnly = true)
    public List<AllocationDto> allocations(Long budgetId) {
        Budget b = require(budgetId);
        return lines(b, periods.require(b.getPeriodId()));
    }

    /**
     * Allocations can be added, edited and deleted on DRAFT, APPROVED and ACTIVE budgets; CLOSED is locked
     * (aligned with reference demo 2026-11-24 (PO)). The allocated total is summed from the lines on every read;
     * the budget amount itself is not changed, and the allocations may never exceed it (ACC_094).
     */
    @Transactional
    public AllocationDto addAllocation(Long budgetId, AllocationForm form) {
        Budget b = requireAllocatable(budgetId);
        BudgetAllocation a = new BudgetAllocation();
        a.setBudgetId(b.getId());
        applyAllocation(b, a, form, -1L);
        a = allocations.save(a);
        return line(a, periods.require(b.getPeriodId()), b.getCompanyId());
    }

    @Transactional
    public AllocationDto updateAllocation(Long budgetId, Long allocationId, AllocationForm form) {
        Budget b = requireAllocatable(budgetId);
        BudgetAllocation a = requireAllocation(b, allocationId);
        applyAllocation(b, a, form, a.getId());
        return line(a, periods.require(b.getPeriodId()), b.getCompanyId());
    }

    @Transactional
    public void deleteAllocation(Long budgetId, Long allocationId) {
        Budget b = requireAllocatable(budgetId);
        allocations.delete(requireAllocation(b, allocationId));
    }

    // ---- monitoring ----

    @Transactional(readOnly = true)
    public MonitoringDto monitoring(Long budgetId) {
        Budget b = require(budgetId);
        return monitoring(b, periods.require(b.getPeriodId()));
    }

    @Transactional(readOnly = true)
    public PeriodMonitoringDto periodMonitoring(Long periodId) {
        BudgetPeriod p = periods.require(periodId);
        List<MonitoringDto> list = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        for (Budget b : budgets.findByPeriodIdAndDeletedFalseOrderByIdAsc(p.getId())) {
            MonitoringDto m = monitoring(b, p);
            list.add(m);
            allocated = allocated.add(m.allocated());
            spent = spent.add(m.spent());
        }
        BigDecimal remaining = allocated.subtract(spent);
        return new PeriodMonitoringDto(p.getId().toString(), p.getName(), p.getStartDate(), p.getEndDate(),
                p.getStatus(), allocated, spent, remaining, variance(remaining, allocated), remaining.signum() < 0,
                list);
    }

    private MonitoringDto monitoring(Budget b, BudgetPeriod p) {
        List<AllocationDto> lines = lines(b, p);
        BigDecimal allocated = lines.stream().map(AllocationDto::allocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = lines.stream().map(AllocationDto::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = allocated.subtract(spent);
        return new MonitoringDto(b.getId().toString(), b.getName(), b.getType(), b.getStatus(), b.getAmount(),
                allocated, spent, remaining, variance(remaining, allocated), remaining.signum() < 0, lines);
    }

    private List<AllocationDto> lines(Budget b, BudgetPeriod p) {
        return allocations.findByBudgetIdOrderByIdAsc(b.getId()).stream().map(a -> line(a, p, b.getCompanyId()))
                .toList();
    }

    private AllocationDto line(BudgetAllocation a, BudgetPeriod p, Long companyId) {
        BigDecimal spent = movement.debitMinusCredit(companyId, movement.withDescendants(companyId, a.getAccountId()),
                p.getStartDate(), p.getEndDate());
        BigDecimal remaining = a.getAllocated().subtract(spent);
        ChartAccount acc = accounts.findById(a.getAccountId()).orElse(null);
        return new AllocationDto(a.getId() == null ? null : a.getId().toString(), a.getBudgetId().toString(),
                a.getAccountId().toString(), acc == null ? null : acc.getCode(), acc == null ? null : acc.getName(),
                a.getAllocated(), spent, remaining, variance(remaining, a.getAllocated()), remaining.signum() < 0,
                a.getNotes());
    }

    /** remaining / allocated × 100: positive = unused, negative = overspent. */
    static BigDecimal variance(BigDecimal remaining, BigDecimal allocated) {
        if (allocated.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return remaining.multiply(HUNDRED).divide(allocated, 2, RoundingMode.HALF_UP);
    }

    // ---- helpers ----

    private static final java.util.Set<EBudgetStatus> ALLOCATABLE =
            java.util.EnumSet.of(EBudgetStatus.DRAFT, EBudgetStatus.APPROVED, EBudgetStatus.ACTIVE);

    Budget require(Long id) {
        Budget b = budgets.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_090));
        TenantGuard.assertSameCompany(b.getCompanyId(), EAccountingError.ACC_090);
        return b;
    }

    /** Status check, and the period must still be open (ACC_089). */
    private Budget requireStatus(Long id, EBudgetStatus status) {
        Budget b = require(id);
        periods.requireOpen(b.getPeriodId());
        if (b.getStatus() != status) {
            throw ApiException.badRequest(EAccountingError.ACC_091, status);
        }
        return b;
    }

    /** Allocations stay editable until the budget is CLOSED; the period must still be open (ACC_089). */
    private Budget requireAllocatable(Long id) {
        Budget b = require(id);
        periods.requireOpen(b.getPeriodId());
        if (!ALLOCATABLE.contains(b.getStatus())) {
            throw ApiException.badRequest(EAccountingError.ACC_091, "DRAFT/APPROVED/ACTIVE");
        }
        return b;
    }

    private BudgetAllocation requireAllocation(Budget b, Long allocationId) {
        return allocations.findById(allocationId).filter(a -> Objects.equals(a.getBudgetId(), b.getId()))
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_092));
    }

    private BigDecimal allocatedTotal(Long budgetId, Long exceptId) {
        return allocations.findByBudgetIdOrderByIdAsc(budgetId).stream()
                .filter(a -> exceptId == null || !Objects.equals(a.getId(), exceptId))
                .map(BudgetAllocation::getAllocated).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void apply(Budget b, BudgetForm f) {
        BudgetPeriod p = periods.requireOpen(f.periodId());
        if (!Objects.equals(p.getCompanyId(), b.getCompanyId())) {
            throw ApiException.notFound(EAccountingError.ACC_086);
        }
        b.setName(f.name().trim());
        b.setPeriodId(p.getId());
        b.setType(f.type());
        b.setAmount(f.amount());
        b.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
    }

    private void applyAllocation(Budget b, BudgetAllocation a, AllocationForm f, Long selfId) {
        ChartAccount acc = accounts.findById(f.accountId())
                .filter(x -> x.isActive() && Objects.equals(x.getCompanyId(), b.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_020, f.accountId()));
        boolean expense = accountTypes.findById(acc.getAccountTypeId())
                .map(t -> t.getCategory() == EAccountCategory.EXPENSES).orElse(false);
        if (!expense) {
            throw ApiException.badRequest(EAccountingError.ACC_093, acc.getCode());
        }
        if (allocations.existsByBudgetIdAndAccountIdAndIdNot(b.getId(), acc.getId(), selfId)) {
            throw ApiException.badRequest(EAccountingError.ACC_095, acc.getCode());
        }
        // Spent includes child accounts, so a parent and its child in one budget would count the child twice.
        java.util.Set<Long> below = movement.withDescendants(b.getCompanyId(), acc.getId());
        for (BudgetAllocation other : allocations.findByBudgetIdOrderByIdAsc(b.getId())) {
            if (Objects.equals(other.getId(), selfId) || Objects.equals(other.getAccountId(), acc.getId())) {
                continue;
            }
            if (below.contains(other.getAccountId())
                    || movement.withDescendants(b.getCompanyId(), other.getAccountId()).contains(acc.getId())) {
                String otherCode = accounts.findById(other.getAccountId()).map(ChartAccount::getCode)
                        .orElse(other.getAccountId().toString());
                throw ApiException.badRequest(EAccountingError.ACC_097, acc.getCode(), otherCode);
            }
        }
        BigDecimal total = allocatedTotal(b.getId(), selfId).add(f.allocated());
        if (total.compareTo(b.getAmount()) > 0) {
            throw ApiException.badRequest(EAccountingError.ACC_094, total, b.getAmount());
        }
        a.setAccountId(acc.getId());
        a.setAllocated(f.allocated());
        a.setNotes(f.notes() == null || f.notes().isBlank() ? null : f.notes().trim());
    }

    private static BudgetDto dto(Budget b) {
        return new BudgetDto(b.getId() == null ? null : b.getId().toString(), b.getCompanyId().toString(),
                b.getPeriodId().toString(), b.getName(), b.getType(), b.getAmount(), b.getDescription(),
                b.getStatus(), b.getApprovedBy() == null ? null : b.getApprovedBy().toString(), b.getApprovedAt(),
                b.getCreatedDate(), b.getUpdatedDate());
    }
}
