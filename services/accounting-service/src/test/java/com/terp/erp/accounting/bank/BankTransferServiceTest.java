package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankTransferService.TransferForm;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankTransferServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);

    private final BankTransferRepository transfers = mock(BankTransferRepository.class);
    private final BankAccountRepository bankAccounts = mock(BankAccountRepository.class);
    private final BankAccountService bank = mock(BankAccountService.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final JournalService journal = mock(JournalService.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private final BankTransferService service = new BankTransferService(transfers, bankAccounts, bank, mapping,
            journal, numbering, clock);
    private final BankTransfer transfer = new BankTransfer();
    private final BankAccount from = account(1L, 21L);
    private final BankAccount to = account(2L, 22L);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "BANK_MANAGE");
        transfer.setId(6L);
        transfer.setCompanyId(10L);
        transfer.setNumber("TRF-2026-11-001");
        transfer.setDate(DAY);
        transfer.setFromAccountId(1L);
        transfer.setToAccountId(2L);
        transfer.setAmount(new BigDecimal("1000"));
        transfer.setCharges(new BigDecimal("11"));
        when(transfers.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(transfer));
        when(bank.require(1L)).thenReturn(from);
        when(bank.require(2L)).thenReturn(to);
        when(bank.glAccountOf(from)).thenReturn(21L);
        when(bank.glAccountOf(to)).thenReturn(22L);
        when(mapping.require(10L, ESystemAccount.BANK_CHARGES)).thenReturn(60L);
        when(journal.post(anyLong(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null, null, null,
                        "BANK_TRANSFER", "6", null, null, null, List.of(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processWritesTwoTransactionsAndChargesLine() {
        var dto = service.process(6L);

        assertThat(dto.status()).isEqualTo(ETransferStatus.COMPLETED);
        verify(bank).record(10L, 1L, ETransactionType.DEBIT, new BigDecimal("1011"), DAY, "TRANSFER_OUT", 6L,
                "TRF-2026-11-001", null);
        verify(bank).record(10L, 2L, ETransactionType.CREDIT, new BigDecimal("1000"), DAY, "TRANSFER_IN", 6L,
                "TRF-2026-11-001", null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalService.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("BANK_TRANSFER"), eq(6L), any(), any(), lines.capture());
        assertThat(lines.getValue()).containsExactly(
                new JournalService.Line(22L, new BigDecimal("1000"), null, "Transfer in"),
                new JournalService.Line(60L, new BigDecimal("11"), null, "Bank charges"),
                new JournalService.Line(21L, null, new BigDecimal("1011"), "Transfer out"));

        assertCode(() -> service.process(6L), EAccountingError.ACC_026);
        assertCode(() -> service.update(6L, form(1L, 2L)), EAccountingError.ACC_026);
        assertCode(() -> service.delete(6L), EAccountingError.ACC_026);
    }

    @Test
    void otherCompanysTransferIsNotFound() {
        transfer.setCompanyId(11L);
        assertThatThrownBy(() -> service.get(6L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        assertCode(() -> service.process(6L), EAccountingError.ACC_024);
        assertCode(() -> service.delete(6L), EAccountingError.ACC_024);
        verify(bank, never()).record(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void sameAccountOnBothSidesRefused() {
        assertCode(() -> service.create(null, form(1L, 1L)), EAccountingError.ACC_025);
        verify(transfers, never()).save(any());
    }

    private static TransferForm form(Long fromId, Long toId) {
        return new TransferForm(DAY, fromId, toId, new BigDecimal("5"), null, null, null);
    }

    private static BankAccount account(Long id, Long gl) {
        BankAccount a = new BankAccount();
        a.setId(id);
        a.setCompanyId(10L);
        a.setGlAccountId(gl);
        return a;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
