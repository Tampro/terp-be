package com.terp.erp.commerce.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.commerce.common.EJournalStatus;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.purchase.PurchaseInvoice;
import com.terp.erp.commerce.purchase.PurchaseInvoicePayment;
import com.terp.erp.commerce.purchase.PurchaseInvoicePaymentRepository;
import com.terp.erp.commerce.purchase.PurchaseInvoicePaymentService;
import com.terp.erp.commerce.purchase.PurchaseInvoiceRepository;
import com.terp.erp.commerce.purchase.PurchaseReturnRepository;
import com.terp.erp.commerce.sales.EDocumentKind;
import com.terp.erp.commerce.sales.EDocumentStatus;
import com.terp.erp.commerce.sales.SalesDocument;
import com.terp.erp.commerce.sales.SalesDocumentRepository;
import com.terp.erp.commerce.sales.SalesInvoicePayment;
import com.terp.erp.commerce.sales.SalesInvoicePaymentRepository;
import com.terp.erp.commerce.sales.SalesInvoicePaymentService;
import com.terp.erp.commerce.salesreturn.SalesReturnRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.EventEnvelope;
import com.terp.erp.outbox.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The consumer runs without a user: no SecurityContext in any of these tests. */
class AccountingEventIntakeTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final SalesDocumentRepository salesDocs = mock(SalesDocumentRepository.class);
    private final SalesReturnRepository salesReturns = mock(SalesReturnRepository.class);
    private final PurchaseInvoiceRepository purchaseDocs = mock(PurchaseInvoiceRepository.class);
    private final PurchaseReturnRepository purchaseReturns = mock(PurchaseReturnRepository.class);
    private final SalesInvoicePaymentRepository salesPayments = mock(SalesInvoicePaymentRepository.class);
    private final PurchaseInvoicePaymentRepository purchasePayments = mock(PurchaseInvoicePaymentRepository.class);
    private final AccountingEventIntake intake = new AccountingEventIntake(processed, salesDocs, salesReturns,
            purchaseDocs, purchaseReturns, new SalesInvoicePaymentService(salesDocs, salesPayments, clock,
                    mock(com.terp.erp.outbox.OutboxPublisher.class)),
            new PurchaseInvoicePaymentService(purchaseDocs, purchasePayments, clock), clock);
    private final Set<String> seen = new java.util.HashSet<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(processed.existsById(any())).thenAnswer(inv -> seen.contains(inv.<String>getArgument(0)));
        when(processed.saveAndFlush(any(ProcessedEvent.class))).thenAnswer(inv -> {
            seen.add(inv.<ProcessedEvent>getArgument(0).getEventId());
            return inv.getArgument(0);
        });
    }

    private EventEnvelope event(String id, String type, Long company, Map<String, Object> data) {
        return new EventEnvelope(id, type, company, "X", "1", Instant.now(clock), "trace-1", mapper.valueToTree(data));
    }

    private SalesDocument invoice(Long id, Long company, String total) {
        SalesDocument d = new SalesDocument();
        d.setId(id);
        d.setCompanyId(company);
        d.setKind(EDocumentKind.INVOICE);
        d.setNumber("SI-2026-11-00" + id);
        d.setCustomerId(5L);
        d.setCustomerName("Test Buyer");
        d.setDocDate(LocalDate.of(2026, 11, 1));
        d.setDueDate(LocalDate.of(2026, 11, 30));
        d.setStatus(EDocumentStatus.POSTED);
        d.setTotal(new BigDecimal(total));
        d.setBalance(new BigDecimal(total));
        d.setJournalStatus(EJournalStatus.PENDING);
        return d;
    }

    @Test
    void journalResultsSetPostedOrFailedAndPostedIsFinal() {
        SalesDocument d = invoice(7L, 10L, "100");
        when(salesDocs.findById(7L)).thenReturn(Optional.of(d));

        intake.accept(event("e1", AccountingEventIntake.JOURNAL_FAILED, 10L,
                Payload.of("sourceType", "SALES_INVOICE", "sourceId", 7L, "errorCode", "ACC_070")));
        assertThat(d.getJournalStatus()).isEqualTo(EJournalStatus.FAILED);
        assertThat(d.getJournalErrorCode()).isEqualTo("ACC_070");

        intake.accept(event("e2", AccountingEventIntake.JOURNAL_POSTED, 10L,
                Payload.of("sourceType", "SALES_INVOICE", "sourceId", 7L, "journalEntryId", "3")));
        assertThat(d.getJournalStatus()).isEqualTo(EJournalStatus.POSTED);
        assertThat(d.getJournalErrorCode()).isNull();

        // a late "failed" (an older request) never undoes a posted journal
        intake.accept(event("e3", AccountingEventIntake.JOURNAL_FAILED, 10L,
                Payload.of("sourceType", "SALES_INVOICE", "sourceId", 7L, "errorCode", "ACC_017")));
        assertThat(d.getJournalStatus()).isEqualTo(EJournalStatus.POSTED);
    }

    @Test
    void journalResultOfAnotherCompanysDocumentChangesNothing() {
        PurchaseInvoice p = new PurchaseInvoice();
        p.setId(40L);
        p.setCompanyId(10L);
        p.setJournalStatus(EJournalStatus.PENDING);
        when(purchaseDocs.findById(40L)).thenReturn(Optional.of(p));

        intake.accept(event("e1", AccountingEventIntake.JOURNAL_FAILED, 11L,
                Payload.of("sourceType", "PURCHASE_INVOICE", "sourceId", 40L, "errorCode", "ACC_070")));
        assertThat(p.getJournalStatus()).isEqualTo(EJournalStatus.PENDING);

        intake.accept(event("e2", AccountingEventIntake.JOURNAL_FAILED, 10L,
                Payload.of("sourceType", "PURCHASE_INVOICE", "sourceId", 40L, "errorCode", "ACC_070")));
        assertThat(p.getJournalStatus()).isEqualTo(EJournalStatus.FAILED);
    }

    @Test
    void customerPaymentClearedPaysEachInvoiceOnceEvenWhenRedelivered() {
        SalesDocument a = invoice(7L, 10L, "100");
        SalesDocument b = invoice(8L, 10L, "300");
        when(salesDocs.lockById(7L)).thenReturn(Optional.of(a));
        when(salesDocs.lockById(8L)).thenReturn(Optional.of(b));
        Map<Long, SalesInvoicePayment> applied = new HashMap<>();
        when(salesPayments.findByInvoiceIdAndPaymentId(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(applied.get(inv.<Long>getArgument(0))));
        when(salesPayments.save(any(SalesInvoicePayment.class))).thenAnswer(inv -> {
            SalesInvoicePayment p = inv.getArgument(0);
            applied.put(p.getInvoiceId(), p);
            return p;
        });
        EventEnvelope cleared = event("e1", AccountingEventIntake.CUSTOMER_PAYMENT_CLEARED, 10L, Payload.of(
                "paymentId", 31L, "allocations", List.of(
                        Payload.of("invoiceId", 7L, "amount", new BigDecimal("100")),
                        Payload.of("invoiceId", 8L, "amount", new BigDecimal("120")))));

        intake.accept(cleared);
        intake.accept(cleared);

        assertThat(a.getStatus()).isEqualTo(EDocumentStatus.PAID);
        assertThat(b.getStatus()).isEqualTo(EDocumentStatus.PARTIAL);
        assertThat(b.getBalance()).isEqualByComparingTo("180");
        verify(salesPayments, times(2)).save(any(SalesInvoicePayment.class));

        // the same payment in a new event (a replay from the DLQ): the (invoice, paymentId) key holds
        intake.accept(event("e2", AccountingEventIntake.CUSTOMER_PAYMENT_CLEARED, 10L, Map.of(
                "paymentId", "31", "allocations", List.of(Map.of("invoiceId", "8", "amount", 120)))));
        assertThat(b.getBalance()).isEqualByComparingTo("180");
        verify(salesPayments, times(2)).save(any(SalesInvoicePayment.class));
    }

    @Test
    void vendorPaymentClearedPaysThePurchaseInvoiceOnceAndRefusesAnotherCompanysInvoice() {
        PurchaseInvoice p = new PurchaseInvoice();
        p.setId(40L);
        p.setCompanyId(10L);
        p.setNumber("PI-2026-11-001");
        p.setVendorId(2L);
        p.setVendorName("Test Supplier");
        p.setInvoiceDate(LocalDate.of(2026, 11, 1));
        p.setDueDate(LocalDate.of(2026, 11, 30));
        p.setStatus(PurchaseInvoice.EStatus.POSTED);
        p.setTotal(new BigDecimal("350"));
        p.setBalance(new BigDecimal("350"));
        when(purchaseDocs.lockById(40L)).thenReturn(Optional.of(p));
        Map<Long, PurchaseInvoicePayment> applied = new HashMap<>();
        when(purchasePayments.findByInvoiceIdAndPaymentId(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(applied.get(inv.<Long>getArgument(0))));
        when(purchasePayments.save(any(PurchaseInvoicePayment.class))).thenAnswer(inv -> {
            PurchaseInvoicePayment x = inv.getArgument(0);
            applied.put(x.getInvoiceId(), x);
            return x;
        });
        Map<String, Object> data = Payload.of("paymentId", 32L, "allocations",
                List.of(Payload.of("invoiceId", 40L, "amount", new BigDecimal("350"))));

        assertThatThrownBy(() -> intake.accept(event("e0", AccountingEventIntake.VENDOR_PAYMENT_CLEARED, 11L, data)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_001);
        verify(purchasePayments, never()).save(any());

        intake.accept(event("e1", AccountingEventIntake.VENDOR_PAYMENT_CLEARED, 10L, data));
        intake.accept(event("e1", AccountingEventIntake.VENDOR_PAYMENT_CLEARED, 10L, data));
        intake.accept(event("e2", AccountingEventIntake.VENDOR_PAYMENT_CLEARED, 10L, data));

        assertThat(p.getStatus()).isEqualTo(PurchaseInvoice.EStatus.PAID);
        assertThat(p.getBalance()).isEqualByComparingTo("0");
        verify(purchasePayments, times(1)).save(any(PurchaseInvoicePayment.class));
    }

    @Test
    void unknownEventTypesAreRecordedAndIgnored() {
        intake.accept(event("e9", "accounting.something.else", 10L, Map.of()));
        verify(processed).saveAndFlush(any(ProcessedEvent.class));
        verify(salesDocs, never()).findById(any());
    }
}
