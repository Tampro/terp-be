package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Transfers between two bank accounts of a company (manual §12.2). Processing writes a DEBIT of amount + charges on
 * the source, a CREDIT of amount on the destination and the entry Dr to-GL amount, Dr BANK_CHARGES charges,
 * Cr from-GL amount + charges.
 */
@Service
@RequiredArgsConstructor
public class BankTransferService {
    static final String SOURCE = "BANK_TRANSFER";

    private final BankTransferRepository transfers;
    private final BankAccountRepository bankAccounts;
    private final BankAccountService bank;
    private final AccountMapping mapping;
    private final JournalService journal;
    private final DocumentNumbering numbering;
    private final Clock clock;

    public record TransferForm(
            @Schema(description = "null = today (UTC)", example = "2026-11-22") LocalDate date,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long fromAccountId,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long toAccountId,
            @Schema(example = "1000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(description = "Bank fee paid by the source account; null = 0", example = "11000")
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal charges,
            @Schema(example = "UNC-001") @Size(max = 100) String reference,
            @Schema(example = "Move to savings") @Size(max = 500) String description) {
    }

    public record TransferDto(String id, String companyId, String number, LocalDate date, String fromAccountId,
                              String toAccountId, BigDecimal amount, BigDecimal charges, String reference,
                              String description, ETransferStatus status, String journalEntryId,
                              Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<TransferDto> search(Long requestedCompanyId, ETransferStatus status, Long accountId, LocalDate from,
                                    LocalDate to, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return transfers.search(companyId, status, accountId, from, to, pageable).map(t -> dto(t, null));
    }

    @Transactional(readOnly = true)
    public TransferDto get(Long id) {
        return dto(require(id), null);
    }

    @Transactional
    public TransferDto create(Long requestedCompanyId, TransferForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        BankTransfer t = new BankTransfer();
        t.setCompanyId(companyId);
        apply(t, form);
        t.setNumber(numbering.next(companyId, "TRF",
                String.format("%04d-%02d", t.getDate().getYear(), t.getDate().getMonthValue())));
        return dto(transfers.save(t), null);
    }

    /** Full replacement; PENDING only (ACC_026). */
    @Transactional
    public TransferDto update(Long id, TransferForm form) {
        BankTransfer t = requirePending(id);
        apply(t, form);
        return dto(t, null);
    }

    @Transactional
    public void delete(Long id) {
        requirePending(id).setDeleted(true);
    }

    /** PENDING → COMPLETED: two bank transactions and one journal entry, all in this transaction. */
    @Transactional
    public TransferDto process(Long id) {
        BankTransfer t = requirePending(id);
        // Lock both accounts in id order so two opposite transfers cannot deadlock.
        bankAccounts.lockById(Math.min(t.getFromAccountId(), t.getToAccountId()));
        bankAccounts.lockById(Math.max(t.getFromAccountId(), t.getToAccountId()));
        BankAccount from = bank.require(t.getFromAccountId());
        BankAccount to = bank.require(t.getToAccountId());
        BigDecimal outflow = t.getAmount().add(t.getCharges());
        bank.record(t.getCompanyId(), from.getId(), ETransactionType.DEBIT, outflow, t.getDate(), "TRANSFER_OUT",
                t.getId(), t.getNumber(), t.getDescription());
        bank.record(t.getCompanyId(), to.getId(), ETransactionType.CREDIT, t.getAmount(), t.getDate(), "TRANSFER_IN",
                t.getId(), t.getNumber(), t.getDescription());
        List<JournalService.Line> lines = new ArrayList<>();
        lines.add(new JournalService.Line(bank.glAccountOf(to), t.getAmount(), null, "Transfer in"));
        if (t.getCharges().signum() > 0) {
            lines.add(new JournalService.Line(mapping.require(t.getCompanyId(), ESystemAccount.BANK_CHARGES),
                    t.getCharges(), null, "Bank charges"));
        }
        lines.add(new JournalService.Line(bank.glAccountOf(from), null, outflow, "Transfer out"));
        var entry = journal.post(t.getCompanyId(), t.getDate(), SOURCE, t.getId(), t.getNumber(),
                t.getDescription(), lines);
        t.setStatus(ETransferStatus.COMPLETED);
        return dto(t, entry.id());
    }

    private BankTransfer require(Long id) {
        BankTransfer t = transfers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_024));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAccountingError.ACC_024);
        return t;
    }

    private BankTransfer requirePending(Long id) {
        BankTransfer t = require(id);
        if (t.getStatus() != ETransferStatus.PENDING) {
            throw ApiException.badRequest(EAccountingError.ACC_026);
        }
        return t;
    }

    private void apply(BankTransfer t, TransferForm f) {
        if (Objects.equals(f.fromAccountId(), f.toAccountId())) {
            throw ApiException.badRequest(EAccountingError.ACC_025);
        }
        for (Long accountId : List.of(f.fromAccountId(), f.toAccountId())) {
            BankAccount a = bank.require(accountId);
            if (!Objects.equals(a.getCompanyId(), t.getCompanyId())) {
                throw ApiException.notFound(EAccountingError.ACC_006);
            }
        }
        t.setDate(f.date() == null ? LocalDate.now(clock) : f.date());
        t.setFromAccountId(f.fromAccountId());
        t.setToAccountId(f.toAccountId());
        t.setAmount(f.amount());
        t.setCharges(f.charges() == null ? BigDecimal.ZERO : f.charges());
        t.setReference(blankToNull(f.reference()));
        t.setDescription(blankToNull(f.description()));
    }

    private static TransferDto dto(BankTransfer t, String journalEntryId) {
        return new TransferDto(t.getId() == null ? null : t.getId().toString(), t.getCompanyId().toString(),
                t.getNumber(), t.getDate(), t.getFromAccountId().toString(), t.getToAccountId().toString(),
                t.getAmount(), t.getCharges(), t.getReference(), t.getDescription(), t.getStatus(), journalEntryId,
                t.getCreatedDate(), t.getUpdatedDate());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
