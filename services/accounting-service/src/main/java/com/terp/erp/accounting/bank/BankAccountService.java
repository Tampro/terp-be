package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Bank accounts and their movements. Every movement is idempotent on (company, sourceType, sourceId) so a caller
 * such as POS can retry safely; the account row is locked while its balance changes.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {
    private final BankAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final ChartAccountRepository chartAccounts;
    private final AccountMapping mapping;
    private final JournalService journal;
    private final Clock clock;

    public record BankAccountForm(
            @Schema(description = "Account name (holder)", example = "Test Holder",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String holderName,
            @Schema(example = "Example Bank", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String bankName,
            @Schema(example = "0000000001", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 50) String accountNumber,
            @Schema(description = "null = 0", example = "1000000")
            @Digits(integer = 15, fraction = 4) BigDecimal openingBalance,
            @Schema(description = "Inactive accounts take no movement", example = "true") boolean active,
            @Schema(example = "Main branch") @Size(max = 255) String branch,
            @Schema(description = "null = CHECKING", example = "CHECKING") EBankAccountType accountType,
            @Schema(description = "Ledger account of this bank account; null = the company's BANK mapping",
                    example = "2") Long glAccountId,
            @Schema(example = "VNPAY") @Size(max = 100) String paymentGateway,
            @Schema(example = "VN00EXAMPLE0000000000") @Size(max = 34) String iban,
            @Schema(example = "EXAMVNVX") @Size(max = 11) String swift,
            @Schema(example = "000000000") @Size(max = 30) String routingNumber) {
    }

    public record IncomeForm(
            @Schema(description = "Kind of document that produced the money", example = "POS_ORDER",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp = "[A-Z_]{1,30}")
            String sourceType,
            @Schema(description = "Id of that document; with sourceType it makes the call idempotent",
                    example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive Long sourceId,
            @Schema(example = "150000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(description = "null = today (UTC)", example = "2026-11-22") LocalDate date,
            @Schema(example = "POS sale") @Size(max = 500) String description) {
    }

    public record BankAccountDto(String id, String companyId, String holderName, String bankName,
                                 String accountNumber, BigDecimal openingBalance, BigDecimal balance, boolean active,
                                 String branch, EBankAccountType accountType, String glAccountId,
                                 String paymentGateway, String iban, String swift, String routingNumber,
                                 Instant createdDate, Instant updatedDate) {
    }

    public record BankTransactionDto(String id, String bankAccountId, ETransactionType type, BigDecimal amount,
                                     LocalDate transactionDate, String sourceType, String sourceId,
                                     String reference, BigDecimal runningBalance, ETransactionStatus status,
                                     String description, Instant createdDate) {
    }

    @Transactional(readOnly = true)
    public Page<BankAccountDto> search(Long requestedCompanyId, Boolean active, String keyword, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return accounts.search(companyId, active, kw(keyword), pageable).map(BankAccountService::dto);
    }

    @Transactional(readOnly = true)
    public BankAccountDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public BankAccountDto create(Long requestedCompanyId, BankAccountForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        BankAccount a = new BankAccount();
        a.setCompanyId(companyId);
        apply(a, form);
        BigDecimal opening = orZero(form.openingBalance());
        a.setOpeningBalance(opening);
        a.setBalance(opening);
        // TODO(assumption): the opening balance is not journalled (no opening-balance equity entry yet, §13).
        return dto(accounts.save(a));
    }

    /** Full replacement; a new opening balance moves the balance by the difference. */
    @Transactional
    public BankAccountDto update(Long id, BankAccountForm form) {
        BankAccount a = requireLocked(id);
        apply(a, form);
        BigDecimal opening = orZero(form.openingBalance());
        a.setBalance(a.getBalance().add(opening.subtract(a.getOpeningBalance())));
        a.setOpeningBalance(opening);
        return dto(a);
    }

    @Transactional
    public void delete(Long id) {
        // TODO(assumption): an account with transactions can still be soft-deleted; its history is kept.
        require(id).setDeleted(true);
    }

    @Transactional(readOnly = true)
    public Page<BankTransactionDto> transactions(Long id, Pageable pageable) {
        BankAccount a = require(id);
        return transactions.findByBankAccountId(a.getId(), pageable).map(BankAccountService::dto);
    }

    @Transactional(readOnly = true)
    public BankTransactionDto getTransaction(Long transactionId) {
        return dto(requireTransaction(transactionId));
    }

    /** CLEARED → RECONCILED (matched to the bank statement); anything else is ACC_023. */
    @Transactional
    public BankTransactionDto reconcile(Long transactionId) {
        BankTransaction t = requireTransaction(transactionId);
        if (t.getStatus() != ETransactionStatus.CLEARED) {
            throw ApiException.badRequest(EAccountingError.ACC_023);
        }
        t.setStatus(ETransactionStatus.RECONCILED);
        return dto(t);
    }

    /**
     * Books POS (or other) income once per (company, sourceType, sourceId): a bank CREDIT and its journal entry
     * Dr bank GL / Cr SALES_REVENUE. A repeat returns the first transaction unchanged.
     */
    @Transactional
    public BankTransactionDto income(Long id, IncomeForm form) {
        // Lock first, then look for the source: two retries of one source on this account are serialized here.
        BankAccount a = requireLocked(id);
        Optional<BankTransaction> existing = transactions.findByCompanyIdAndSourceTypeAndSourceId(
                a.getCompanyId(), form.sourceType(), form.sourceId());
        if (existing.isPresent()) {
            // TODO(assumption): a repeat with another account or amount returns the first transaction silently.
            return dto(existing.get());
        }
        LocalDate date = form.date() == null ? LocalDate.now(clock) : form.date();
        BankTransactionDto t = record(a.getCompanyId(), a.getId(), ETransactionType.CREDIT, form.amount(), date,
                form.sourceType(), form.sourceId(), null, form.description());
        // TODO(assumption): POS does not send the VAT split, so the gross amount goes to sales revenue; output VAT
        // is not separated until POS sends net + VAT.
        journal.post(a.getCompanyId(), date, form.sourceType(), form.sourceId(), null,
                form.description() == null ? "POS income" : form.description(), List.of(
                        new JournalService.Line(glAccountOf(a), form.amount(), null, null),
                        new JournalService.Line(mapping.require(a.getCompanyId(), ESystemAccount.SALES_REVENUE),
                                null, form.amount(), null)));
        return t;
    }

    /**
     * Records a money movement on a bank account in the caller's transaction. Idempotent on (company, sourceType,
     * sourceId): a repeat returns the existing transaction and does not move the balance. Locks the account row;
     * CREDIT adds, DEBIT subtracts; writes runningBalance and status CLEARED. ACC_006 (404) when the account is
     * not the company's, ACC_008 when it is inactive.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BankTransactionDto record(Long companyId, Long bankAccountId, ETransactionType type, BigDecimal amount,
                                     LocalDate date, String sourceType, Long sourceId, String reference,
                                     String description) {
        BankAccount a = accounts.lockById(bankAccountId)
                .filter(x -> Objects.equals(x.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_006));
        Optional<BankTransaction> existing = transactions.findByCompanyIdAndSourceTypeAndSourceId(companyId,
                sourceType, sourceId);
        if (existing.isPresent()) {
            return dto(existing.get());
        }
        if (!a.isActive()) {
            throw ApiException.badRequest(EAccountingError.ACC_008);
        }
        // TODO(assumption): a DEBIT may take the balance below zero (overdraft, credit and loan accounts).
        a.setBalance(type == ETransactionType.CREDIT ? a.getBalance().add(amount) : a.getBalance().subtract(amount));
        BankTransaction t = new BankTransaction();
        t.setCompanyId(companyId);
        t.setBankAccountId(a.getId());
        t.setType(type);
        t.setAmount(amount);
        t.setTransactionDate(date);
        t.setSourceType(sourceType);
        t.setSourceId(sourceId);
        t.setReference(blankToNull(reference));
        t.setDescription(blankToNull(description));
        t.setRunningBalance(a.getBalance());
        t.setStatus(ETransactionStatus.CLEARED);
        return dto(transactions.save(t));
    }

    /** Ledger account of a bank account: its own GL account, else the company's BANK mapping. */
    public Long glAccountOf(BankAccount a) {
        return a.getGlAccountId() != null ? a.getGlAccountId() : mapping.require(a.getCompanyId(), ESystemAccount.BANK);
    }

    /** A bank account of the caller's company (ACC_006 otherwise, 404 on direct access). */
    public BankAccount require(Long id) {
        return checked(accounts.findByIdAndDeletedFalse(id));
    }

    /** Same as require, holding the row lock until the transaction ends. */
    private BankAccount requireLocked(Long id) {
        return checked(accounts.lockById(id));
    }

    private BankTransaction requireTransaction(Long id) {
        BankTransaction t = transactions.findById(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_022));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAccountingError.ACC_022);
        return t;
    }

    private static BankAccount checked(Optional<BankAccount> row) {
        BankAccount a = row.orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_006));
        TenantGuard.assertSameCompany(a.getCompanyId(), EAccountingError.ACC_006);
        return a;
    }

    private void apply(BankAccount a, BankAccountForm f) {
        String number = f.accountNumber().trim();
        // TODO(assumption): checked in the service only (soft-deleted rows keep their number); two concurrent
        // creates with one number can both pass.
        if (accounts.existsByCompanyIdAndAccountNumberAndDeletedFalseAndIdNot(a.getCompanyId(), number,
                a.getId() == null ? -1L : a.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_007);
        }
        Long gl = f.glAccountId() != null ? f.glAccountId() : mapping.require(a.getCompanyId(), ESystemAccount.BANK);
        if (chartAccounts.findById(gl).filter(x -> x.isActive()
                && Objects.equals(x.getCompanyId(), a.getCompanyId())).isEmpty()) {
            throw ApiException.badRequest(EAccountingError.ACC_020, gl);
        }
        a.setHolderName(f.holderName().trim());
        a.setBankName(f.bankName().trim());
        a.setAccountNumber(number);
        a.setActive(f.active());
        a.setBranch(blankToNull(f.branch()));
        a.setAccountType(f.accountType() == null ? EBankAccountType.CHECKING : f.accountType());
        a.setGlAccountId(gl);
        a.setPaymentGateway(blankToNull(f.paymentGateway()));
        a.setIban(blankToNull(f.iban()));
        a.setSwift(blankToNull(f.swift()));
        a.setRoutingNumber(blankToNull(f.routingNumber()));
    }

    private static BankAccountDto dto(BankAccount a) {
        return new BankAccountDto(a.getId().toString(), a.getCompanyId().toString(), a.getHolderName(),
                a.getBankName(), a.getAccountNumber(), a.getOpeningBalance(), a.getBalance(), a.isActive(),
                a.getBranch(), a.getAccountType(), a.getGlAccountId() == null ? null : a.getGlAccountId().toString(),
                a.getPaymentGateway(), a.getIban(), a.getSwift(), a.getRoutingNumber(),
                a.getCreatedDate(), a.getUpdatedDate());
    }

    static BankTransactionDto dto(BankTransaction t) {
        return new BankTransactionDto(t.getId().toString(), t.getBankAccountId().toString(), t.getType(),
                t.getAmount(), t.getTransactionDate(), t.getSourceType(), t.getSourceId().toString(),
                t.getReference(), t.getRunningBalance(), t.getStatus(), t.getDescription(), t.getCreatedDate());
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** LIKE pattern for a keyword, '' when none; ! escapes % and _. */
    private static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
