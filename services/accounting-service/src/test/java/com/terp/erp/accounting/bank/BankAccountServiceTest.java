package com.terp.erp.accounting.bank;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankAccountService.BankAccountForm;
import com.terp.erp.accounting.bank.BankAccountService.IncomeForm;
import com.terp.erp.accounting.common.EAccountingError;
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
    private final BankAccountService service = new BankAccountService(accounts, transactions, clock);
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
        saved.setType(ETransactionType.INCOME);
        saved.setAmount(new BigDecimal("50"));
        saved.setSourceType("POS_ORDER");
        saved.setSourceId(42L);
        when(transactions.findByCompanyIdAndSourceTypeAndSourceId(10L, "POS_ORDER", 42L))
                .thenReturn(Optional.of(saved));
        var second = service.income(3L, income());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(account.getBalance()).isEqualByComparingTo("150");
        verify(transactions, times(1)).save(any());
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
                new BigDecimal("120"), true));
        assertThat(account.getBalance()).isEqualByComparingTo("200");
        assertThat(account.getOpeningBalance()).isEqualByComparingTo("120");
    }

    private static BankAccountForm form(String number) {
        return new BankAccountForm("Test Holder", "Example Bank", number, BigDecimal.ZERO, true);
    }

    private static IncomeForm income() {
        return new IncomeForm("POS_ORDER", 42L, new BigDecimal("50"), null, "POS sale");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
