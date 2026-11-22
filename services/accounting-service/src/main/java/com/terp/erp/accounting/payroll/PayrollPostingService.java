package com.terp.erp.accounting.payroll;

import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.BankAccountService.BankTransactionDto;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.ledger.JournalService.JournalEntryDto;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Pays a completed payroll run from a bank account (manual 15 "Pay salary", FR-ERP-PAY-004): one bank DEBIT and one
 * journal entry Dr SALARY_PAYABLE (334) / Cr the bank's GL account, both keyed on ("PAYROLL_RUN", runId) so a retry
 * from payroll-service books nothing twice.
 * Before the payment it books the salary expense accrual Dr SALARY_EXPENSE (6421) / Cr SALARY_PAYABLE (334) for the
 * same amount, source ("PAYROLL_ACCRUAL", runId), dated the run's period end, so 334 nets to zero once paid.
 * TODO(assumption): the accrual is posted with the payment, not when the run is processed: a COMPLETED run's totals
 *  still change (entries removed, run cancelled) until it is paid. One net line; the gross / employee deductions /
 *  insurance split (3383, 3335 ...) waits for the chief accountant. Runs paid before this change have no accrual.
 * TODO(assumption): one bank debit for the run total, not one per employee.
 */
@Service
@RequiredArgsConstructor
public class PayrollPostingService {
    public static final String SOURCE_TYPE = "PAYROLL_RUN";
    public static final String ACCRUAL_SOURCE_TYPE = "PAYROLL_ACCRUAL";

    private final BankAccountService banks;
    private final JournalService journal;
    private final AccountMapping mapping;
    private final Clock clock;

    public record PayrollPostingForm(
            @Schema(description = "The payroll run id (payroll-service); the idempotency key", example = "12",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long runId,
            @Schema(description = "Bank account of the company paying the salaries", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long bankAccountId,
            @Schema(description = "Total net pay of the run", example = "25300000",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(description = "Payment date; omitted = today (UTC)", example = "2026-12-05") LocalDate date,
            @Schema(example = "PAYROLL-12") @Size(max = 100) String reference,
            @Schema(example = "Salaries November 2026") @Size(max = 500) String description,
            @Schema(description = "Date of the salary expense accrual (the run's period end); omitted = the payment "
                    + "date", example = "2026-11-30") LocalDate accrualDate) {
        public PayrollPostingForm(Long runId, Long bankAccountId, BigDecimal amount, LocalDate date, String reference,
                                  String description) {
            this(runId, bankAccountId, amount, date, reference, description, null);
        }
    }

    public record PayrollPostingDto(String runId, BankTransactionDto bankTransaction, String journalEntryId,
                                    String journalNumber) {
    }

    @Transactional
    public PayrollPostingDto post(Long requestedCompanyId, PayrollPostingForm f) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        // 404 ACC_006 for another company's account.
        BankAccount bank = banks.require(f.bankAccountId());
        TenantGuard.assertSameCompany(bank.getCompanyId(), EAccountingError.ACC_006);
        LocalDate date = f.date() == null ? LocalDate.now(clock) : f.date();
        String description = f.description() == null || f.description().isBlank()
                ? "Payroll run " + f.runId() : f.description();
        LocalDate accrualDate = f.accrualDate() == null || f.accrualDate().isAfter(date) ? date : f.accrualDate();
        Long payable = mapping.require(companyId, ESystemAccount.SALARY_PAYABLE);
        journal.post(companyId, accrualDate, ACCRUAL_SOURCE_TYPE, f.runId(), f.reference(),
                "Salary expense - " + description, List.of(
                        new JournalService.Line(mapping.require(companyId, ESystemAccount.SALARY_EXPENSE),
                                f.amount(), null, null),
                        new JournalService.Line(payable, null, f.amount(), null)));
        // TODO(assumption): a repeat with another account or amount returns the first booking unchanged.
        BankTransactionDto t = banks.record(companyId, bank.getId(), ETransactionType.DEBIT, f.amount(), date,
                SOURCE_TYPE, f.runId(), f.reference(), description);
        JournalEntryDto e = journal.post(companyId, date, SOURCE_TYPE, f.runId(), f.reference(), description, List.of(
                new JournalService.Line(payable, f.amount(), null, null),
                new JournalService.Line(banks.glAccountOf(bank), null, f.amount(), null)));
        return new PayrollPostingDto(f.runId().toString(), t, e.id(), e.number());
    }
}
