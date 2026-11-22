package com.terp.erp.accounting.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.bank.BankAccount;
import com.terp.erp.accounting.bank.BankAccountService;
import com.terp.erp.accounting.bank.ETransactionType;
import com.terp.erp.accounting.client.CommerceClient;
import com.terp.erp.accounting.client.CommerceClient.ESide;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerService;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.DocumentNumbering;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.payment.PaymentService.AllocationForm;
import com.terp.erp.accounting.payment.PaymentService.PaymentForm;
import com.terp.erp.accounting.vendor.VendorService;
import com.terp.erp.exception.ApiException;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AccountNoteRepository notes = mock(AccountNoteRepository.class);
    private final CommerceClient commerce = mock(CommerceClient.class);
    private final BankAccountService bank = mock(BankAccountService.class);
    private final CustomerService customers = mock(CustomerService.class);
    private final VendorService vendors = mock(VendorService.class);
    private final DocumentNumbering numbering = mock(DocumentNumbering.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final JournalService journal = mock(JournalService.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final PaymentService service = new PaymentService(payments, notes, commerce, bank, customers, vendors,
            numbering, mapping, journal, clock, outbox);

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setId(5L);
        c.setCompanyId(10L);
        c.setCompanyName("Test Buyer");
        when(customers.require(5L)).thenReturn(c);
        BankAccount b = new BankAccount();
        b.setId(2L);
        b.setCompanyId(10L);
        b.setActive(true);
        b.setGlAccountId(1121L);
        when(bank.require(2L)).thenReturn(b);
        when(numbering.next(anyLong(), anyString(), anyString())).thenAnswer(i -> i.getArgument(1) + "-2026-11-001");
        when(payments.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(31L);
            return p;
        });
        when(mapping.require(10L, ESystemAccount.RECEIVABLE)).thenReturn(131L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void invoice(String balance, String status) {
        when(commerce.invoice(ESide.SALES, 9L)).thenReturn(new CommerceClient.Invoice(9L, "SI-2026-11-001", 5L,
                "Test Buyer", DAY, DAY.plusDays(30), BigDecimal.ZERO, new BigDecimal(balance),
                new BigDecimal(balance), status));
    }

    private static PaymentForm form(String amount) {
        return new PaymentForm(5L, 2L, DAY, "Transfer 0001", null,
                List.of(new AllocationForm(9L, new BigDecimal(amount))), null);
    }

    private Payment pending(Long company, EPaymentStatus status) {
        Payment p = new Payment();
        p.setId(31L);
        p.setCompanyId(company);
        p.setKind(EPaymentKind.CUSTOMER);
        p.setNumber("CP-2026-11-001");
        p.setPaymentDate(DAY);
        p.setPartyId(5L);
        p.setPartyName("Test Buyer");
        p.setBankAccountId(2L);
        p.setStatus(status);
        PaymentAllocation a = new PaymentAllocation();
        a.setInvoiceId(9L);
        a.setAmount(new BigDecimal("500"));
        p.getAllocations().add(a);
        p.setAllocatedTotal(new BigDecimal("500"));
        p.setAmount(new BigDecimal("500"));
        when(payments.lockById(31L)).thenReturn(Optional.of(p));
        when(payments.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(p));
        return p;
    }

    private static void fails(Runnable call, HttpStatus status, EAccountingError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(code);
            assertThat(((ApiException) e).getStatus()).isEqualTo(status);
        });
    }

    @Test
    void allocationOverTheInvoiceBalanceIsRefused() {
        TestLogin.as(10L, "PAYMENT_MANAGE");
        invoice("500", "POSTED");

        fails(() -> service.create(EPaymentKind.CUSTOMER, null, form("500.01")), HttpStatus.BAD_REQUEST,
                EAccountingError.ACC_054);
        verify(payments, never()).save(any());
    }

    @Test
    void allocationToAPaidInvoiceIsRefused() {
        TestLogin.as(10L, "PAYMENT_MANAGE");
        invoice("0", "PAID");

        fails(() -> service.create(EPaymentKind.CUSTOMER, null, form("1")), HttpStatus.BAD_REQUEST,
                EAccountingError.ACC_053);
    }

    @Test
    void createKeepsThePaymentPendingWithCashEqualToAllocations() {
        TestLogin.as(10L, "PAYMENT_MANAGE");
        invoice("500", "POSTED");

        PaymentService.PaymentDto dto = service.create(EPaymentKind.CUSTOMER, null, form("500"));

        assertThat(dto.status()).isEqualTo(EPaymentStatus.PENDING);
        assertThat(dto.number()).isEqualTo("CP-2026-11-001");
        assertThat(dto.amount()).isEqualByComparingTo("500");
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
    }

    /** GWT FR-ACC-PAY-003: SI balance 500, bank B; clear 500 → SI paid, Credit 500 on B, Dr B / Cr receivable 500. */
    @Test
    void clearPaysTheInvoiceRecordsTheBankCreditAndPostsABalancedEntry() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        Payment p = pending(10L, EPaymentStatus.PENDING);
        invoice("500", "POSTED");

        PaymentService.PaymentDto dto = service.clear(EPaymentKind.CUSTOMER, 31L);

        assertThat(dto.status()).isEqualTo(EPaymentStatus.CLEARED);
        assertThat(p.getClearedAt()).isNotNull();
        // commerce is only read here; the invoice is paid by commerce from the event below
        verify(commerce).invoice(ESide.SALES, 9L);
        verify(bank).record(eq(10L), eq(2L), eq(ETransactionType.CREDIT), eq(new BigDecimal("500")), eq(DAY),
                eq("CUSTOMER_PAYMENT"), eq(31L), eq("CP-2026-11-001"), anyString());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalService.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(journal).post(eq(10L), eq(DAY), eq("CUSTOMER_PAYMENT"), eq(31L), eq("CP-2026-11-001"), anyString(),
                lines.capture());
        List<JournalService.Line> l = lines.getValue();
        assertThat(l).hasSize(2);
        assertThat(l.get(0).accountId()).isEqualTo(1121L);
        assertThat(l.get(0).debit()).isEqualByComparingTo("500");
        assertThat(l.get(1).accountId()).isEqualTo(131L);
        assertThat(l.get(1).credit()).isEqualByComparingTo("500");
        BigDecimal dr = l.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = l.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dr).isEqualByComparingTo(cr);
        verify(outbox).publish(eq(10L), eq("accounting.customer_payment.cleared"), eq("CUSTOMER_PAYMENT"), eq(31L),
                org.mockito.ArgumentMatchers.<Object>argThat(x -> ((java.util.Map<?, ?>) x).get("number")
                        .equals("CP-2026-11-001")
                        && ((java.util.Map<?, ?>) x).get("allocations").equals(List.of(
                        com.terp.erp.outbox.Payload.of("invoiceId", 9L, "amount", new BigDecimal("500"))))));
    }

    @Test
    void clearingAVendorPaymentPublishesItsAllocationsForThePurchaseInvoices() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        Payment p = pending(10L, EPaymentStatus.PENDING);
        p.setKind(EPaymentKind.VENDOR);
        p.setNumber("VP-2026-11-001");
        when(commerce.invoice(ESide.PURCHASE, 9L)).thenReturn(new CommerceClient.Invoice(9L, "PI-2026-11-001", 5L,
                "Test Supplier", DAY, DAY.plusDays(30), BigDecimal.ZERO, new BigDecimal("800"),
                new BigDecimal("800"), "PARTIAL"));
        when(mapping.require(10L, ESystemAccount.PAYABLE)).thenReturn(331L);

        assertThat(service.clear(EPaymentKind.VENDOR, 31L).status()).isEqualTo(EPaymentStatus.CLEARED);

        verify(bank).record(eq(10L), eq(2L), eq(ETransactionType.DEBIT), eq(new BigDecimal("500")), eq(DAY),
                eq("VENDOR_PAYMENT"), eq(31L), eq("VP-2026-11-001"), anyString());
        verify(outbox).publish(eq(10L), eq("accounting.vendor_payment.cleared"), eq("VENDOR_PAYMENT"), eq(31L),
                org.mockito.ArgumentMatchers.<Object>argThat(x -> ((java.util.Map<?, ?>) x).get("allocations")
                        .equals(List.of(com.terp.erp.outbox.Payload.of("invoiceId", 9L, "amount",
                                new BigDecimal("500"))))));
    }

    @Test
    void anInvoiceNoLongerOpenRefusesTheClearBeforeAnyWrite() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        Payment p = pending(10L, EPaymentStatus.PENDING);
        invoice("0", "PAID");

        fails(() -> service.clear(EPaymentKind.CUSTOMER, 31L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_053);
        invoice("100", "PARTIAL");
        fails(() -> service.clear(EPaymentKind.CUSTOMER, 31L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_054);
        assertThat(p.getStatus()).isEqualTo(EPaymentStatus.PENDING);
        verify(journal, never()).post(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyList());
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void clearingTwiceIsRefusedAndTouchesNothing() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        pending(10L, EPaymentStatus.CLEARED);

        fails(() -> service.clear(EPaymentKind.CUSTOMER, 31L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_051);
        verify(commerce, never()).invoice(any(), anyLong());
        verify(journal, never()).post(anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyList());
    }

    @Test
    void commerceFailureLeavesThePaymentPendingAndNothingLocalIsRecorded() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        Payment p = pending(10L, EPaymentStatus.PENDING);
        org.mockito.Mockito.doThrow(ApiException.badRequest(EAccountingError.ACC_060)).when(commerce)
                .invoice(ESide.SALES, 9L);

        assertThatThrownBy(() -> service.clear(EPaymentKind.CUSTOMER, 31L)).isInstanceOf(ApiException.class);
        assertThat(p.getStatus()).isEqualTo(EPaymentStatus.PENDING);
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
        verify(bank, never()).record(anyLong(), anyLong(), any(), any(), any(), anyString(), anyLong(), anyString(),
                anyString());
    }

    @Test
    void onlyAPendingPaymentCanBeCancelled() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        Payment p = pending(10L, EPaymentStatus.PENDING);
        assertThat(service.cancel(EPaymentKind.CUSTOMER, 31L).status()).isEqualTo(EPaymentStatus.CANCELLED);
        assertThat(p.getCancelledAt()).isNotNull();

        fails(() -> service.cancel(EPaymentKind.CUSTOMER, 31L), HttpStatus.BAD_REQUEST, EAccountingError.ACC_051);
    }

    @Test
    void anotherCompanysPaymentIsNotFound() {
        TestLogin.as(11L, "PAYMENT_VIEW", "PAYMENT_CLEAR");
        pending(10L, EPaymentStatus.PENDING);

        fails(() -> service.get(EPaymentKind.CUSTOMER, 31L), HttpStatus.NOT_FOUND, EAccountingError.ACC_050);
        fails(() -> service.clear(EPaymentKind.CUSTOMER, 31L), HttpStatus.NOT_FOUND, EAccountingError.ACC_050);
        verify(commerce, never()).invoice(any(), anyLong());
    }

    @Test
    void aVendorPaymentIdIsNotFoundAsACustomerPayment() {
        TestLogin.as(10L, "PAYMENT_VIEW");
        pending(10L, EPaymentStatus.PENDING).setKind(EPaymentKind.VENDOR);

        fails(() -> service.get(EPaymentKind.CUSTOMER, 31L), HttpStatus.NOT_FOUND, EAccountingError.ACC_050);
    }
}
