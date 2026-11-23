package com.terp.erp.commerce.sales;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.InvoicePaymentForm;
import com.terp.erp.commerce.purchase.PurchaseInvoice;
import com.terp.erp.commerce.purchase.PurchaseInvoicePayment;
import com.terp.erp.commerce.purchase.PurchaseInvoicePaymentRepository;
import com.terp.erp.commerce.purchase.PurchaseInvoicePaymentService;
import com.terp.erp.commerce.purchase.PurchaseInvoiceRepository;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesInvoicePaymentServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final SalesDocumentRepository documents = mock(SalesDocumentRepository.class);
    private final SalesInvoicePaymentRepository payments = mock(SalesInvoicePaymentRepository.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final SalesInvoicePaymentService service = new SalesInvoicePaymentService(documents, payments, clock,
            outbox);
    private final PurchaseInvoiceRepository purchaseDocs = mock(PurchaseInvoiceRepository.class);
    private final PurchaseInvoicePaymentRepository purchasePayments = mock(PurchaseInvoicePaymentRepository.class);
    private final PurchaseInvoicePaymentService purchase = new PurchaseInvoicePaymentService(purchaseDocs,
            purchasePayments, clock);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SalesDocument invoice(Long company, String total, String balance, EDocumentStatus status) {
        SalesDocument d = new SalesDocument();
        d.setId(9L);
        d.setCompanyId(company);
        d.setKind(EDocumentKind.INVOICE);
        d.setNumber("SI-2026-11-001");
        d.setCustomerId(5L);
        d.setCustomerName("Test Buyer");
        d.setDocDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 11, 30));
        d.setStatus(status);
        d.setTotal(new BigDecimal(total));
        d.setPaidAmount(new BigDecimal(total).subtract(new BigDecimal(balance)));
        d.setBalance(new BigDecimal(balance));
        when(documents.lockById(9L)).thenReturn(Optional.of(d));
        return d;
    }

    private static void fails(Runnable call, HttpStatus status, Object code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(code);
            assertThat(((ApiException) e).getStatus()).isEqualTo(status);
        });
    }

    @Test
    void paymentOfTheWholeBalanceMarksPaid() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        SalesDocument d = invoice(10L, "500", "500", EDocumentStatus.POSTED);
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.empty());

        service.apply(9L, new InvoicePaymentForm(31L, new BigDecimal("500")));

        assertThat(d.getBalance()).isEqualByComparingTo("0");
        assertThat(d.getStatus()).isEqualTo(EDocumentStatus.PAID);
        verify(payments).save(any(SalesInvoicePayment.class));
    }

    @Test
    void aStatusChangePublishesInvoiceStatusChangedOnce() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        invoice(10L, "500", "500", EDocumentStatus.POSTED);
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.empty());
        service.apply(9L, new InvoicePaymentForm(31L, new BigDecimal("100")));
        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("sales.invoice.status_changed"),
                org.mockito.ArgumentMatchers.eq("SALES_INVOICE"), org.mockito.ArgumentMatchers.eq(9L),
                payload.capture());
        var p = Payloads.map(payload.getValue());
        assertThat(p.get("title")).isEqualTo("SI-2026-11-001");
        assertThat(p.get("fromStatus")).isEqualTo(EDocumentStatus.POSTED);
        assertThat(p.get("status")).isEqualTo(EDocumentStatus.PARTIAL);
        assertThat(p).containsKeys("invoiceId", "customerName", "balance");

        // PARTIAL -> PARTIAL: no second event
        when(payments.findByInvoiceIdAndPaymentId(9L, 32L)).thenReturn(Optional.empty());
        service.apply(9L, new InvoicePaymentForm(32L, new BigDecimal("100")));
        verify(outbox, org.mockito.Mockito.times(1)).publish(any(), any(), any(), any(), any());
    }

    @Test
    void partPaymentMarksPartial() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        SalesDocument d = invoice(10L, "1000", "1000", EDocumentStatus.POSTED);
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.empty());

        service.apply(9L, new InvoicePaymentForm(31L, new BigDecimal("400")));

        assertThat(d.getBalance()).isEqualByComparingTo("600");
        assertThat(d.getStatus()).isEqualTo(EDocumentStatus.PARTIAL);
    }

    @Test
    void repeatWithTheSamePaymentIdAppliesNothingTwice() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        SalesDocument d = invoice(10L, "500", "0", EDocumentStatus.PAID);
        SalesInvoicePayment done = new SalesInvoicePayment();
        done.setAmount(new BigDecimal("500"));
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.of(done));

        service.apply(9L, new InvoicePaymentForm(31L, new BigDecimal("500.0000")));

        assertThat(d.getBalance()).isEqualByComparingTo("0");
        assertThat(d.getPaidAmount()).isEqualByComparingTo("500");
        verify(payments, never()).save(any());
    }

    @Test
    void amountOverTheBalanceIsRefused() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        invoice(10L, "500", "500", EDocumentStatus.POSTED);
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.empty());

        fails(() -> service.apply(9L, new InvoicePaymentForm(31L, new BigDecimal("500.01"))),
                HttpStatus.BAD_REQUEST, ECommerceError.SAL_022);
        verify(payments, never()).save(any());
    }

    @Test
    void draftInvoiceIsRefused() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        invoice(10L, "500", "500", EDocumentStatus.DRAFT);
        when(payments.findByInvoiceIdAndPaymentId(9L, 31L)).thenReturn(Optional.empty());

        fails(() -> service.apply(9L, new InvoicePaymentForm(31L, BigDecimal.TEN)), HttpStatus.BAD_REQUEST,
                ECommerceError.SAL_021);
    }

    @Test
    void anotherCompanysInvoiceIsNotFound() {
        TestLogin.as(11L, "PAYMENT_CLEAR");
        invoice(10L, "500", "500", EDocumentStatus.POSTED);

        fails(() -> service.apply(9L, new InvoicePaymentForm(31L, BigDecimal.TEN)), HttpStatus.NOT_FOUND,
                ECommerceError.SAL_001);
    }

    @Test
    void purchaseInvoicePaymentOverBalanceRefusedAndExactBalancePaid() {
        TestLogin.as(10L, "PAYMENT_CLEAR");
        PurchaseInvoice d = new PurchaseInvoice();
        d.setId(4L);
        d.setCompanyId(10L);
        d.setVendorId(3L);
        d.setVendorName("Test Supplier");
        d.setInvoiceDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 11, 30));
        d.setStatus(PurchaseInvoice.EStatus.POSTED);
        d.setTotal(new BigDecimal("300"));
        d.setBalance(new BigDecimal("300"));
        when(purchaseDocs.lockById(4L)).thenReturn(Optional.of(d));
        when(purchasePayments.findByInvoiceIdAndPaymentId(4L, 8L)).thenReturn(Optional.empty());

        fails(() -> purchase.apply(4L, new InvoicePaymentForm(8L, new BigDecimal("301"))), HttpStatus.BAD_REQUEST,
                EPurchaseError.PUR_015);
        purchase.apply(4L, new InvoicePaymentForm(8L, new BigDecimal("300")));

        assertThat(d.getStatus()).isEqualTo(PurchaseInvoice.EStatus.PAID);
        verify(purchasePayments).save(any(PurchaseInvoicePayment.class));
    }
}
