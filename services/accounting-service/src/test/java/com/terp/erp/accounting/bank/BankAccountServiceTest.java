package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankAccountService.BankAccountForm;
import com.terp.erp.accounting.bank.BankAccountService.IncomeForm;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankAccountServiceTest {
    private final BankAccountRepository accounts = mock(BankAccountRepository.class);
    private final BankTransactionRepository transactions = mock(BankTransactionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private final ChartAccountRepository chartAccounts = mock(ChartAccountRepository.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final JournalService journal = mock(JournalService.class);
    private final BankAccountService service = new BankAccountService(accounts, transactions, chartAccounts, mapping,
            journal, clock);
    private final BankAccount account = new BankAccount();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "BANK_MANAGE");
        account.setId(3L);
        account.setCompanyId(10L);
        account.setHolderName("Test Holder");
        account.setBankName("Example Bank");
        account.setAccountNumber("0000000001");
        account.setOpeningBalance(new BigDecimal("100"));
        account.setBalance(new BigDecimal("100"));
        when(accounts.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(account));
        when(accounts.lockById(3L)).thenReturn(Optional.of(account));
        ChartAccount gl = new ChartAccount();
        gl.setId(2L);
        gl.setCompanyId(10L);
        when(chartAccounts.findById(2L)).thenReturn(Optional.of(gl));
        when(mapping.require(10L, ESystemAccount.BANK)).thenReturn(2L);
        when(mapping.require(10L, ESystemAccount.SALES_REVENUE)).thenReturn(40L);
        when(transactions.save(any())).thenAnswer(i -> {
            BankTransaction t = i.getArgument(0);
            t.setId(9L);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void otherCompanysAccountIsNotFound() {
        account.setCompanyId(11L);
        assertCode(() -> service.get(3L), EAccountingError.ACC_006);
        assertCode(() -> service.update(3L, form("0000000001")), EAccountingError.ACC_006);
        assertCode(() -> service.delete(3L), EAccountingError.ACC_006);
        assertCode(() -> service.income(3L, income()), EAccountingError.ACC_006);
        assertThat(account.isDeleted()).isFalse();
        assertThat(account.getBalance()).isEqualByComparingTo("100");
        assertThatThrownBy(() -> service.get(3L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void sameSourceTwiceRaisesBalanceOnce() {
        when(transactions.findByCompanyIdAndSourceTypeAndSourceId(10L, "POS_ORDER", 42L)).thenReturn(Optional.empty());
        var first = service.income(3L, income());
        assertThat(account.getBalance()).isEqualByComparingTo("150");
        assertThat(first.transactionDate()).isEqualTo(LocalDate.of(2026, 11, 22));

        BankTransaction saved = new BankTransaction();
        saved.setId(9L);
        saved.setCompanyId(10L);
        saved.setBankAccountId(3L);
        saved.setType(ETransactionType.CREDIT);
        saved.setAmount(new BigDecimal("50"));
        saved.setSourceType("POS_ORDER");
        saved.setSourceId(42L);
        when(transactions.findByCompanyIdAndSourceTypeAndSourceId(10L, "POS_ORDER", 42L))
                .thenReturn(Optional.of(saved));
        var second = service.income(3L, income());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(account.getBalance()).isEqualByComparingTo("150");
        verify(transactions, times(1)).save(any());
        assertThat(first.type()).isEqualTo(ETransactionType.CREDIT);
        assertThat(first.runningBalance()).isEqualByComparingTo("150");
        verify(journal, times(1)).post(eq(10L), eq(LocalDate.of(2026, 11, 22)), eq("POS_ORDER"), eq(42L), any(),
                any(), eq(List.of(new JournalService.Line(2L, new BigDecimal("50"), null, null),
                        new JournalService.Line(40L, null, new BigDecimal("50"), null))));
    }

    @Test
    void reconcileOnlyFromCleared() {
        BankTransaction t = new BankTransaction();
        t.setId(9L);
        t.setCompanyId(10L);
        t.setBankAccountId(3L);
        t.setSourceId(1L);
        t.setStatus(ETransactionStatus.CLEARED);
        when(transactions.findById(9L)).thenReturn(Optional.of(t));
        assertThat(service.reconcile(9L).status()).isEqualTo(ETransactionStatus.RECONCILED);
        assertCode(() -> service.reconcile(9L), EAccountingError.ACC_023);
        t.setCompanyId(11L);
        assertCode(() -> service.reconcile(9L), EAccountingError.ACC_022);
    }

    @Test
    void recordDebitSubtractsAndRefusesOtherCompany() {
        when(transactions.findByCompanyIdAndSourceTypeAndSourceId(10L, "EXPENSE", 7L)).thenReturn(Optional.empty());
        var t = service.record(10L, 3L, ETransactionType.DEBIT, new BigDecimal("30"), LocalDate.of(2026, 11, 22),
                "EXPENSE", 7L, "EXP-1", null);
        assertThat(account.getBalance()).isEqualByComparingTo("70");
        assertThat(t.runningBalance()).isEqualByComparingTo("70");
        assertThat(t.status()).isEqualTo(ETransactionStatus.CLEARED);
        assertThatThrownBy(() -> service.record(11L, 3L, ETransactionType.CREDIT, BigDecimal.ONE,
                LocalDate.of(2026, 11, 22), "EXPENSE", 8L, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void inactiveAccountTakesNoIncome() {
        account.setActive(false);
        when(transactions.findByCompanyIdAndSourceTypeAndSourceId(10L, "POS_ORDER", 42L)).thenReturn(Optional.empty());
        assertCode(() -> service.income(3L, income()), EAccountingError.ACC_008);
        verify(transactions, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void duplicateAccountNumberRefused() {
        when(accounts.existsByCompanyIdAndAccountNumberAndDeletedFalseAndIdNot(eq(10L), eq("0000000001"), anyLong()))
                .thenReturn(true);
        assertCode(() -> service.create(null, form("0000000001")), EAccountingError.ACC_007);
        verify(accounts, never()).save(any());
    }

    @Test
    void openingBalanceChangeMovesBalanceByDifference() {
        account.setBalance(new BigDecimal("180"));
        service.update(3L, new BankAccountForm("Test Holder", "Example Bank", "0000000001",
                new BigDecimal("120"), true, null, null, 2L, null, null, null, null));
        assertThat(account.getBalance()).isEqualByComparingTo("200");
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("120");
    }

    private static BankAccountForm form(String number) {
        return new BankAccountForm("Test Holder", "Example Bank", number, BigDecimal.ZERO, true, null, null, 2L, null,
                null, null, null);
    }

    private static IncomeForm income() {
        return new IncomeForm("POS_ORDER", 42L, new BigDecimal("50"), null, "POS sale");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
