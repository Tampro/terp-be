package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.common.EAccountingError;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Bank accounts and their income. Income is idempotent on (company, sourceType, sourceId) so a caller such as
 * POS can retry safely; the account row is locked while its balance changes.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {
    private final BankAccountRepository accounts;
    private final BankTransactionRepository transactions;
    private final Clock clock;

    public record BankAccountForm(
            @Schema(example = "Test Holder", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String holderName,
            @Schema(example = "Example Bank", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String bankName,
            @Schema(example = "0000000001", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 50) String accountNumber,
            @Schema(description = "null = 0", example = "1000000")
            @Digits(integer = 15, fraction = 4) BigDecimal openingBalance,
            @Schema(description = "Inactive accounts take no income", example = "true") boolean active) {
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
                                 Instant createdDate, Instant updatedDate) {
    }

    public record BankTransactionDto(String id, String bankAccountId, ETransactionType type, BigDecimal amount,
                                     LocalDate transactionDate, String sourceType, String sourceId,
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

    /** Books income once per (company, sourceType, sourceId); a repeat returns the first transaction unchanged. */
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
        if (!a.isActive()) {
            throw ApiException.badRequest(EAccountingError.ACC_008);
        }
        BankTransaction t = new BankTransaction();
        t.setCompanyId(a.getCompanyId());
        t.setBankAccountId(a.getId());
        t.setType(ETransactionType.INCOME);
        t.setAmount(form.amount());
        t.setTransactionDate(form.date() == null ? LocalDate.now(clock) : form.date());
        t.setSourceType(form.sourceType());
        t.setSourceId(form.sourceId());
        t.setDescription(blankToNull(form.description()));
        a.setBalance(a.getBalance().add(form.amount()));
        return dto(transactions.save(t));
    }

    /** A bank account of the caller's company (ACC_006 otherwise, 404 on direct access). */
    public BankAccount require(Long id) {
        return checked(accounts.findByIdAndDeletedFalse(id));
    }

    /** Same as require, holding the row lock until the transaction ends. */
    private BankAccount requireLocked(Long id) {
        return checked(accounts.lockById(id));
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
        a.setHolderName(f.holderName().trim());
        a.setBankName(f.bankName().trim());
        a.setAccountNumber(number);
        a.setActive(f.active());
    }

    private static BankAccountDto dto(BankAccount a) {
        return new BankAccountDto(a.getId().toString(), a.getCompanyId().toString(), a.getHolderName(),
                a.getBankName(), a.getAccountNumber(), a.getOpeningBalance(), a.getBalance(), a.isActive(),
                a.getCreatedDate(), a.getUpdatedDate());
    }

    private static BankTransactionDto dto(BankTransaction t) {
        return new BankTransactionDto(t.getId().toString(), t.getBankAccountId().toString(), t.getType(),
                t.getAmount(), t.getTransactionDate(), t.getSourceType(), t.getSourceId().toString(),
                t.getDescription(), t.getCreatedDate());
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
