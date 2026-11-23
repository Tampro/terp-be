package com.terp.erp.accounting.income;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.coa.AccountCategory;
import com.terp.erp.accounting.coa.CategoryService;
import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.ECategoryKind;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.income.CashVoucherService.VoucherForm;
import com.terp.erp.accounting.ledger.DocumentNumbering;
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

class CashVoucherServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);

    private final CashVoucherRepository vouchers = mock(CashVoucherRepository.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final BankAccountService bank = mock(BankAccountService.class);
    private final JournalService journal = mock(JournalService.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private final com.terp.erp.outbox.OutboxPublisher outbox = org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final CashVoucherService service = new CashVoucherService(vouchers, categories, accounts, bank, journal,
            numbering, clock, outbox);
    private final CashVoucher revenue = new CashVoucher();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "REVENUE_MANAGE", "REVENUE_APPROVE");
        revenue.setId(4L);
        revenue.setCompanyId(10L);
        revenue.setKind(ECategoryKind.REVENUE);
        revenue.setNumber("REV-2026-11-001");
        revenue.setDate(DAY);
        revenue.setCategoryId(1L);
        revenue.setBankAccountId(3L);
        revenue.setAccountId(40L);
        revenue.setAmount(new BigDecimal("500"));
        when(vouchers.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(revenue));
        BankAccount b = new BankAccount();
        b.setId(3L);
        b.setCompanyId(10L);
        when(bank.require(3L)).thenReturn(b);
        when(bank.glAccountOf(b)).thenReturn(2L);
        when(journal.post(anyLong(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null, null, null,
                        "REVENUE", "4", null, new BigDecimal("500"), new BigDecimal("500"), List.of(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postFromApprovedWritesCreditAndBalancedEntry() {
        revenue.setStatus(EVoucherStatus.APPROVED);
        var dto = service.post(ECategoryKind.REVENUE, 4L);

        assertThat(dto.status()).isEqualTo(EVoucherStatus.POSTED);
        assertThat(dto.journalEntryId()).isEqualTo("50");
        verify(bank).record(10L, 3L, ETransactionType.CREDIT, new BigDecimal("500"), DAY, "REVENUE", 4L,
                "REV-2026-11-001", null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalService.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("REVENUE"), eq(4L), eq("REV-2026-11-001"), any(), lines.capture());
        assertThat(lines.getValue()).containsExactly(
                new JournalService.Line(2L, new BigDecimal("500"), null, null),
                new JournalService.Line(40L, null, new BigDecimal("500"), null));
    }

    @Test
    void postOnlyFromApproved() {
        revenue.setStatus(EVoucherStatus.DRAFT);
        assertCode(() -> service.post(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_030);
        revenue.setStatus(EVoucherStatus.POSTED);
        assertCode(() -> service.post(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_030);
        verify(bank, never()).record(anyLong(), anyLong(), any(), any(), any(), any(), anyLong(), any(), any());
        verify(journal, never()).post(anyLong(), any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    void approveSetsApproverAndOnlyFromDraft() {
        var dto = service.approve(ECategoryKind.REVENUE, 4L);
        assertThat(dto.status()).isEqualTo(EVoucherStatus.APPROVED);
        assertThat(dto.approvedBy()).isEqualTo("5");
        assertCode(() -> service.approve(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_029);
        assertCode(() -> service.delete(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_029);
    }

    @Test
    void otherCompanysRevenueIsNotFound() {
        revenue.setCompanyId(11L);
        revenue.setStatus(EVoucherStatus.APPROVED);
        assertThatThrownBy(() -> service.get(ECategoryKind.REVENUE, 4L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        assertCode(() -> service.post(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_027);
        assertCode(() -> service.approve(ECategoryKind.REVENUE, 4L), EAccountingError.ACC_027);
        // An expense id does not reach a revenue either.
        revenue.setCompanyId(10L);
        assertCode(() -> service.get(ECategoryKind.EXPENSE, 4L), EAccountingError.ACC_028);
    }

    @Test
    void createDefaultsAccountToCategoryGl() {
        AccountCategory c = new AccountCategory();
        c.setId(1L);
        c.setCompanyId(10L);
        c.setKind(ECategoryKind.REVENUE);
        c.setGlAccountId(40L);
        when(categories.require(ECategoryKind.REVENUE, 1L)).thenReturn(c);
        ChartAccount gl = new ChartAccount();
        gl.setId(40L);
        gl.setCompanyId(10L);
        when(accounts.findById(40L)).thenReturn(Optional.of(gl));
        when(numbering.next(10L, "REV", "2026-11")).thenReturn("REV-2026-11-002");
        when(vouchers.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = service.create(ECategoryKind.REVENUE, null,
                new VoucherForm(null, 1L, 3L, null, new BigDecimal("10"), null, null));
        {
            var ev = org.mockito.ArgumentCaptor.forClass(Object.class);
            org.mockito.Mockito.verify(outbox, org.mockito.Mockito.atLeastOnce()).publish(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("accounting.revenue.created"), org.mockito.ArgumentMatchers.eq("REVENUE"),
                    org.mockito.ArgumentMatchers.any(), ev.capture());
            @SuppressWarnings("unchecked") var p = (java.util.Map<String, Object>) ev.getValue();
            org.assertj.core.api.Assertions.assertThat(p).containsKeys("revenueId", "title", "number", "amount", "date");
        }
        assertThat(dto.accountId()).isEqualTo("40");
        assertThat(dto.number()).isEqualTo("REV-2026-11-002");
        assertThat(dto.status()).isEqualTo(EVoucherStatus.DRAFT);
        assertThat(dto.date()).isEqualTo(DAY);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
