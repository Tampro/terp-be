package com.terp.erp.employee.lifecycle;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.lifecycle.EmployeeTrip.EReimbursementStatus;
import com.terp.erp.employee.lifecycle.LifecycleSupport.FileRef;
import com.terp.erp.employee.lifecycle.LifecycleSupport.Ref;
import com.terp.erp.employee.lifecycle.TripService.Expenses;
import com.terp.erp.employee.lifecycle.TripService.FinancialSummary;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Trip Expenses page (PO, 2026-11-20): lines with a type from the company's expense-types list, dated within the trip
 * (EMP_065). Lines cannot change once the reimbursement is PAID (EMP_063) or the trip is cancelled (EMP_060).
 */
@Service
@RequiredArgsConstructor
public class TripExpenseService {
    private final TripExpenseRepository expenses;
    private final TripService trips;
    private final LifecycleCatalogService catalogs;
    private final LifecycleCatalogRepository catalogItems;
    private final LifecycleSupport support;

    public record ExpenseDto(String id, Ref expenseType, LocalDate expenseDate, String description,
                             BigDecimal amount, String receiptId, FileRef receipt, Instant createdDate) {
    }

    /** The page: lines by date with the trip's totals. */
    public record TripExpensesDto(String tripId, List<ExpenseDto> items, Expenses expenses,
                                  FinancialSummary financialSummary) {
    }

    public record ExpenseForm(
            @Schema(description = "Entry of /catalogs/expense-types", example = "4",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long expenseTypeId,
            @Schema(example = "2026-12-02", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalDate expenseDate,
            @Schema(example = "Taxi to the client") @Size(max = 500) String description,
            @Schema(description = "Company currency", example = "250000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Schema(description = "Media library file id of the receipt") Long receiptMediaId) {
    }

    @Transactional(readOnly = true)
    public TripExpensesDto list(Long tripId) {
        return page(trips.require(tripId));
    }

    @Transactional
    public TripExpensesDto create(Long tripId, ExpenseForm form) {
        EmployeeTrip t = requireOpen(tripId);
        TripExpense x = new TripExpense();
        apply(t, x, form);
        expenses.save(x);
        trips.refreshExpenses(t);
        return page(t);
    }

    /** Full replacement of the line. */
    @Transactional
    public TripExpensesDto update(Long tripId, Long id, ExpenseForm form) {
        EmployeeTrip t = requireOpen(tripId);
        apply(t, require(t, id), form);
        expenses.flush();
        trips.refreshExpenses(t);
        return page(t);
    }

    @Transactional
    public TripExpensesDto delete(Long tripId, Long id) {
        EmployeeTrip t = requireOpen(tripId);
        require(t, id).setDeleted(true);
        expenses.flush();
        trips.refreshExpenses(t);
        return page(t);
    }

    private void apply(EmployeeTrip t, TripExpense x, ExpenseForm f) {
        if (f.expenseDate().isBefore(t.getStartDate()) || f.expenseDate().isAfter(t.getEndDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_065);
        }
        Long typeId = catalogs.usable(ELifecycleCatalogKind.EXPENSE_TYPE, f.expenseTypeId(), t.getCompanyId(),
                x.getExpenseTypeId()).getId();
        x.setCompanyId(t.getCompanyId());
        x.setTripId(t.getId());
        x.setExpenseTypeId(typeId);
        x.setExpenseDate(f.expenseDate());
        x.setDescription(AwardService.blankToNull(f.description()));
        x.setAmount(f.amount());
        x.setReceiptMediaId(support.file(f.receiptMediaId(), t.getCompanyId()));
    }

    private TripExpensesDto page(EmployeeTrip t) {
        List<TripExpense> lines = expenses.findByTripIdAndDeletedFalseOrderByExpenseDateAscIdAsc(t.getId());
        Map<Long, String> types = catalogItems.findAllById(lines.stream().map(TripExpense::getExpenseTypeId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(LifecycleCatalog::getId, LifecycleCatalog::getName));
        List<ExpenseDto> items = lines.stream().map(x -> new ExpenseDto(x.getId().toString(),
                new Ref(x.getExpenseTypeId().toString(), types.get(x.getExpenseTypeId())), x.getExpenseDate(),
                x.getDescription(), x.getAmount(),
                x.getReceiptMediaId() == null ? null : x.getReceiptMediaId().toString(),
                support.fileRef(x.getReceiptMediaId()), x.getCreatedDate())).toList();
        return new TripExpensesDto(t.getId().toString(), items,
                new Expenses(t.getExpensesTotal(), t.getReimbursementStatus()), TripService.summary(t));
    }

    private EmployeeTrip requireOpen(Long tripId) {
        EmployeeTrip t = trips.requireActive(tripId);
        if (t.getReimbursementStatus() == EReimbursementStatus.PAID) {
            throw ApiException.badRequest(EEmployeeError.EMP_063);
        }
        return t;
    }

    private TripExpense require(EmployeeTrip t, Long id) {
        return expenses.findByIdAndTripIdAndDeletedFalse(id, t.getId())
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_066));
    }
}
