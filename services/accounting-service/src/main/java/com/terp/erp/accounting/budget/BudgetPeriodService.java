package com.terp.erp.accounting.budget;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Budget periods (FR-BUD-001): ACTIVE (edit, delete when empty) → close after the end date → CLOSED. */
@Service
@RequiredArgsConstructor
public class BudgetPeriodService {
    /** TODO(assumption): "within one span" read as at most one (leap) year. */
    static final int MAX_DAYS = 366;

    private final BudgetPeriodRepository periods;
    private final BudgetRepository budgets;
    private final Clock clock;

    public record PeriodForm(
            @Schema(example = "FY2026", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(example = "2026", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(1900) @Max(9999)
            Integer financialYear,
            @Schema(example = "2026-01-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate startDate,
            @Schema(example = "2026-12-31", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate endDate) {
    }

    public record PeriodDto(String id, String companyId, String name, Integer financialYear, LocalDate startDate,
                            LocalDate endDate, EBudgetPeriodStatus status, Instant closedAt, Instant createdDate,
                            Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<PeriodDto> search(Long requestedCompanyId, EBudgetPeriodStatus status, Integer financialYear,
                                  Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return periods.search(companyId, status, financialYear, pageable).map(BudgetPeriodService::dto);
    }

    @Transactional(readOnly = true)
    public PeriodDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public PeriodDto create(Long requestedCompanyId, PeriodForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        BudgetPeriod p = new BudgetPeriod();
        p.setCompanyId(companyId);
        apply(p, form);
        return dto(periods.save(p));
    }

    /** Full replacement; ACTIVE only (ACC_089). */
    @Transactional
    public PeriodDto update(Long id, PeriodForm form) {
        BudgetPeriod p = requireOpen(id);
        apply(p, form);
        return dto(p);
    }

    /** Soft delete; ACTIVE and without live budgets (ACC_096). */
    @Transactional
    public void delete(Long id) {
        BudgetPeriod p = requireOpen(id);
        if (budgets.existsByPeriodIdAndDeletedFalse(p.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_096);
        }
        p.setDeleted(true);
    }

    /** FR-BUD-001: only after the end date (ACC_088). */
    @Transactional
    public PeriodDto close(Long id) {
        BudgetPeriod p = requireOpen(id);
        if (!LocalDate.now(clock).isAfter(p.getEndDate())) {
            throw ApiException.badRequest(EAccountingError.ACC_088, p.getEndDate());
        }
        p.setStatus(EBudgetPeriodStatus.CLOSED);
        p.setClosedAt(Instant.now(clock));
        return dto(p);
    }

    BudgetPeriod require(Long id) {
        BudgetPeriod p = periods.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_086));
        TenantGuard.assertSameCompany(p.getCompanyId(), EAccountingError.ACC_086);
        return p;
    }

    BudgetPeriod requireOpen(Long id) {
        BudgetPeriod p = require(id);
        if (p.getStatus() == EBudgetPeriodStatus.CLOSED) {
            throw ApiException.badRequest(EAccountingError.ACC_089);
        }
        return p;
    }

    private static void apply(BudgetPeriod p, PeriodForm f) {
        if (!f.endDate().isAfter(f.startDate())
                || ChronoUnit.DAYS.between(f.startDate(), f.endDate()) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(EAccountingError.ACC_087, MAX_DAYS);
        }
        p.setName(f.name().trim());
        p.setFinancialYear(f.financialYear());
        p.setStartDate(f.startDate());
        p.setEndDate(f.endDate());
    }

    static PeriodDto dto(BudgetPeriod p) {
        return new PeriodDto(p.getId() == null ? null : p.getId().toString(), p.getCompanyId().toString(),
                p.getName(), p.getFinancialYear(), p.getStartDate(), p.getEndDate(), p.getStatus(), p.getClosedAt(),
                p.getCreatedDate(), p.getUpdatedDate());
    }
}
