package com.terp.erp.employee.lifecycle;

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
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EAdvanceStatus;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EReimbursementStatus;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Trips page (docs/analysis/06 §7.2.7). Statuses are set by hand (PO, 2026-11-20); a CANCELLED trip can only be
 * viewed or deleted (EMP_060). The advance is requested with the trip and its amount is fixed once approved
 * (EMP_062); expenses are lines of the Trip Expenses page, fixed once reimbursed. Amounts are in the company currency.
 */
@Service
@RequiredArgsConstructor
public class TripService {
    /**
     * TODO(assumption): the move table is ours (the manual lists only the statuses; statuses were set freely before)
     * — confirm with BA. COMPLETED and CANCELLED are final.
     */
    static final Map<EStatus, java.util.Set<EStatus>> MOVES = Map.of(
            EStatus.PLANNED, java.util.EnumSet.of(EStatus.ONGOING, EStatus.CANCELLED),
            EStatus.ONGOING, java.util.EnumSet.of(EStatus.COMPLETED, EStatus.CANCELLED));

    private final EmployeeTripRepository trips;
    private final TripExpenseRepository expenses;
    private final LifecycleSupport support;

    public record Advance(BigDecimal amount, EAdvanceStatus status) {
    }

    public record Expenses(BigDecimal total, EReimbursementStatus reimbursementStatus) {
    }

    /**
     * netBalance = expenses − advance: positive = the company owes the employee, negative = the employee returns
     * money; settlement = |netBalance| (PO, 2026-11-20). progress (0–100) = share of the advance (RECONCILED) and the
     * expenses (PAID) that are settled.
     */
    public record FinancialSummary(BigDecimal advance, BigDecimal expensesTotal, BigDecimal settlement,
                                   BigDecimal netBalance, int progress, boolean settled) {
    }

    /** document only on the details. */
    public record TripDto(String id, EmployeeRef employee, String purpose, String destination, LocalDate startDate,
                          LocalDate endDate, long days, String description, String expectedOutcomes,
                          String documentId, FileRef document, EStatus status, Advance advance, Expenses expenses,
                          FinancialSummary financialSummary, Instant createdDate, Instant updatedDate) {
    }

    /** The five cards and the status tabs. */
    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record TripForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "Client meeting", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String purpose,
            @Schema(example = "Da Nang", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String destination,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-12-05", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Size(max = 5000) String description,
            @Size(max = 5000) String expectedOutcomes,
            @Schema(description = "Media library file id") Long documentMediaId,
            @Schema(description = "Company currency; omitted or 0 = no advance. Fixed once the advance is approved",
                    example = "5000000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal advanceAmount) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record AdvanceStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                                    EAdvanceStatus status) {
    }

    public record ReimbursementStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                                          EReimbursementStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<TripDto> search(Long requestedCompanyId, String keyword, Long employeeId, LocalDate startFrom,
                                LocalDate endTo, EStatus status, Pageable pageable) {
        Page<EmployeeTrip> page = trips.search(LifecycleSupport.company(requestedCompanyId), employeeId, startFrom,
                endTo, status, DepartmentService.kw(keyword), pageable);
        Map<Long, EmployeeRef> people = support.refs(page.map(EmployeeTrip::getEmployeeId).toSet());
        return page.map(t -> dto(t, people.get(t.getEmployeeId()), false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long employeeId, LocalDate startFrom,
                                     LocalDate endTo) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : trips.countByStatus(LifecycleSupport.company(requestedCompanyId), employeeId, startFrom,
                endTo, DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public TripDto get(Long id) {
        return detail(require(id));
    }

    /** Starts as PLANNED; an advance amount makes the advance REQUESTED. */
    @Transactional
    public TripDto create(TripForm form) {
        EmployeeTrip t = new EmployeeTrip();
        apply(t, form);
        return detail(trips.save(t));
    }

    /** Full replacement of the form while PLANNED (EMP_102 afterwards); statuses and expenses are kept. */
    @Transactional
    public TripDto update(Long id, TripForm form) {
        EmployeeTrip t = require(id);
        if (t.getStatus() != EStatus.PLANNED) {
            throw ApiException.badRequest(EEmployeeError.EMP_102, t.getStatus().name());
        }
        apply(t, form);
        return detail(t);
    }

    /** Follows {@link #MOVES} (400 EMP_096 otherwise); CANCELLED keeps answering EMP_060. */
    @Transactional
    public TripDto changeStatus(Long id, EStatus status) {
        EmployeeTrip t = requireActive(id);
        LifecycleSupport.assertMove(MOVES, t.getStatus(), status);
        t.setStatus(status);
        return detail(t);
    }

    @Transactional
    public TripDto changeAdvanceStatus(Long id, EAdvanceStatus status) {
        EmployeeTrip t = requireActive(id);
        if (t.getAdvanceAmount() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_061);
        }
        t.setAdvanceStatus(status);
        return detail(t);
    }

    @Transactional
    public TripDto changeReimbursementStatus(Long id, EReimbursementStatus status) {
        EmployeeTrip t = requireActive(id);
        if (t.getReimbursementStatus() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_064);
        }
        t.setReimbursementStatus(status);
        return detail(t);
    }

    /**
     * PLANNED or CANCELLED only (a cancelled trip shows View and Delete, PO 2026-11-20); an ONGOING or COMPLETED trip
     * carries advances and expenses and stays (EMP_102). TODO(assumption): a cancelled trip may be deleted even with a
     * paid advance — confirm with BA.
     */
    @Transactional
    public void delete(Long id) {
        EmployeeTrip t = require(id);
        if (t.getStatus() != EStatus.PLANNED && t.getStatus() != EStatus.CANCELLED) {
            throw ApiException.badRequest(EEmployeeError.EMP_102, t.getStatus().name());
        }
        t.setDeleted(true);
    }

    private void apply(EmployeeTrip t, TripForm f) {
        Employee e = support.employee(f.employeeId());
        if (t.getId() == null) {
            EmployeeExit.assertEmployed(e);
        }
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_052, "end date", "start date");
        }
        BigDecimal amount = f.advanceAmount() == null || f.advanceAmount().signum() == 0 ? null : f.advanceAmount();
        if (t.getAdvanceStatus() != null && t.getAdvanceStatus() != EAdvanceStatus.REQUESTED
                && !sameAmount(amount, t.getAdvanceAmount())) {
            throw ApiException.badRequest(EEmployeeError.EMP_062);
        }
        t.setCompanyId(e.getCompanyId());
        t.setEmployeeId(e.getId());
        t.setPurpose(f.purpose().trim());
        t.setDestination(f.destination().trim());
        t.setStartDate(f.startDate());
        t.setEndDate(f.endDate());
        t.setDescription(AwardService.blankToNull(f.description()));
        t.setExpectedOutcomes(AwardService.blankToNull(f.expectedOutcomes()));
        t.setDocumentMediaId(support.file(f.documentMediaId(), e.getCompanyId()));
        t.setAdvanceAmount(amount);
        if (amount == null) {
            t.setAdvanceStatus(null);
        } else if (t.getAdvanceStatus() == null) {
            t.setAdvanceStatus(EAdvanceStatus.REQUESTED);
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Called by the expense lines after each change. */
    void refreshExpenses(EmployeeTrip t) {
        t.setExpensesTotal(expenses.total(t.getId()));
        boolean any = expenses.countByTripIdAndDeletedFalse(t.getId()) > 0;
        if (!any) {
            t.setReimbursementStatus(null);
        } else if (t.getReimbursementStatus() == null) {
            t.setReimbursementStatus(EReimbursementStatus.PENDING);
        }
    }

    static FinancialSummary summary(EmployeeTrip t) {
        BigDecimal advance = t.getAdvanceAmount() == null ? BigDecimal.ZERO : t.getAdvanceAmount();
        BigDecimal net = t.getExpensesTotal().subtract(advance);
        int parts = 0;
        int done = 0;
        if (t.getAdvanceStatus() != null) {
            parts++;
            done += t.getAdvanceStatus() == EAdvanceStatus.RECONCILED ? 1 : 0;
        }
        if (t.getReimbursementStatus() != null) {
            parts++;
            done += t.getReimbursementStatus() == EReimbursementStatus.PAID ? 1 : 0;
        }
        // TODO(assumption): nothing to settle (no advance, no expense) counts as settled — confirm with BA.
        int progress = parts == 0 ? 100 : done * 100 / parts;
        return new FinancialSummary(advance, t.getExpensesTotal(), net.abs(), net, progress, progress == 100);
    }

    private TripDto detail(EmployeeTrip t) {
        return dto(t, support.refs(List.of(t.getEmployeeId())).get(t.getEmployeeId()), true);
    }

    private TripDto dto(EmployeeTrip t, EmployeeRef employee, boolean withFile) {
        return new TripDto(t.getId().toString(), employee, t.getPurpose(), t.getDestination(), t.getStartDate(),
                t.getEndDate(), ChronoUnit.DAYS.between(t.getStartDate(), t.getEndDate()) + 1, t.getDescription(),
                t.getExpectedOutcomes(), t.getDocumentMediaId() == null ? null : t.getDocumentMediaId().toString(),
                withFile ? support.fileRef(t.getDocumentMediaId()) : null, t.getStatus(),
                t.getAdvanceStatus() == null ? null : new Advance(t.getAdvanceAmount(), t.getAdvanceStatus()),
                new Expenses(t.getExpensesTotal(), t.getReimbursementStatus()), summary(t), t.getCreatedDate(),
                t.getUpdatedDate());
    }

    EmployeeTrip requireActive(Long id) {
        EmployeeTrip t = require(id);
        if (t.getStatus() == EStatus.CANCELLED) {
            throw ApiException.badRequest(EEmployeeError.EMP_060);
        }
        return t;
    }

    EmployeeTrip require(Long id) {
        EmployeeTrip t = trips.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_059));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_059);
        return t;
    }
}
