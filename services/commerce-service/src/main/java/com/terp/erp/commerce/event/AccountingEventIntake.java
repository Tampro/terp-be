package com.terp.erp.commerce.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.commerce.common.EJournalStatus;
import com.terp.erp.commerce.common.InvoicePaymentForm;
import com.terp.erp.commerce.common.JournalTracked;
import com.terp.erp.commerce.purchase.PurchaseInvoiceRepository;
import com.terp.erp.commerce.purchase.PurchaseInvoicePaymentService;
import com.terp.erp.commerce.purchase.PurchaseReturnRepository;
import com.terp.erp.commerce.sales.EDocumentKind;
import com.terp.erp.commerce.sales.SalesDocumentRepository;
import com.terp.erp.commerce.sales.SalesInvoicePaymentService;
import com.terp.erp.commerce.salesreturn.SalesReturnRepository;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Applies one accounting event to commerce, once: the processed_event row (key = eventId) and the effect commit in
 * one transaction, so a redelivered message changes nothing. The consumer runs without a user: the company comes
 * from the envelope and every row is matched against it (no TenantGuard, no permission check — accounting checked
 * PAYMENT_CLEAR, commerce checked SALES_POST / PURCHASE_POST when the event chain started).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEventIntake {
    public static final String JOURNAL_POSTED = "accounting.journal.posted";
    public static final String JOURNAL_FAILED = "accounting.journal.failed";
    public static final String CUSTOMER_PAYMENT_CLEARED = "accounting.customer_payment.cleared";
    public static final String VENDOR_PAYMENT_CLEARED = "accounting.vendor_payment.cleared";
    /** Routing keys bound to erp.commerce.accounting. */
    public static final List<String> ROUTING_KEYS = List.of(JOURNAL_POSTED, JOURNAL_FAILED, CUSTOMER_PAYMENT_CLEARED,
            VENDOR_PAYMENT_CLEARED);

    private final ProcessedEventRepository processed;
    private final SalesDocumentRepository salesDocuments;
    private final SalesReturnRepository salesReturns;
    private final PurchaseInvoiceRepository purchaseInvoices;
    private final PurchaseReturnRepository purchaseReturns;
    private final SalesInvoicePaymentService salesPayments;
    private final PurchaseInvoicePaymentService purchasePayments;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && processed.existsById(eventId);
    }

    /**
     * A business refusal (ApiException: invoice not open, amount over the balance…) rolls everything back and is
     * thrown to the consumer, which dead-letters the message: a payment is applied to all its invoices or to none.
     */
    @Transactional
    public void accept(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.eventType() == null || e.companyId() == null || e.data() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed event");
        }
        if (processed.existsById(e.eventId())) {
            return;
        }
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setReceivedAt(Instant.now(clock));
        processed.saveAndFlush(pe);
        switch (e.eventType()) {
            case JOURNAL_POSTED -> journal(e, true);
            case JOURNAL_FAILED -> journal(e, false);
            case CUSTOMER_PAYMENT_CLEARED -> payments(e, true);
            case VENDOR_PAYMENT_CLEARED -> payments(e, false);
            default -> log.debug("Event type {} ignored", e.eventType());
        }
    }

    /** POSTED is final: a late or duplicated "failed" after a successful retry changes nothing. */
    private void journal(EventEnvelope e, boolean posted) {
        String sourceType = e.data().path("sourceType").asText("");
        Long sourceId = id(e.data().path("sourceId"));
        Optional<? extends JournalTracked> doc = sourceId == null ? Optional.empty() : switch (sourceType) {
            case "SALES_INVOICE" -> salesDocuments.findById(sourceId)
                    .filter(d -> d.getKind() == EDocumentKind.INVOICE);
            case "SALES_RETURN" -> salesReturns.findById(sourceId);
            case "PURCHASE_INVOICE" -> purchaseInvoices.findById(sourceId);
            case "PURCHASE_RETURN" -> purchaseReturns.findById(sourceId);
            default -> Optional.empty();
        };
        if (doc.isEmpty() || !e.companyId().equals(doc.get().getCompanyId())) {
            log.warn("Journal result {} for unknown document {} {} ignored", e.eventId(), sourceType, sourceId);
            return;
        }
        JournalTracked d = doc.get();
        if (posted) {
            d.setJournalStatus(EJournalStatus.POSTED);
            d.setJournalErrorCode(null);
        } else if (d.getJournalStatus() != EJournalStatus.POSTED) {
            d.setJournalStatus(EJournalStatus.FAILED);
            d.setJournalErrorCode(e.data().path("errorCode").asText(null));
        }
    }

    private void payments(EventEnvelope e, boolean customer) {
        Long paymentId = id(e.data().path("paymentId"));
        if (paymentId == null) {
            throw new AmqpRejectAndDontRequeueException("payment event without paymentId");
        }
        for (JsonNode a : e.data().path("allocations")) {
            Long invoiceId = id(a.path("invoiceId"));
            if (invoiceId == null || a.path("amount").isMissingNode() || a.path("amount").isNull()) {
                throw new AmqpRejectAndDontRequeueException("payment allocation without invoice or amount");
            }
            InvoicePaymentForm f = new InvoicePaymentForm(paymentId, new BigDecimal(a.path("amount").asText()));
            if (customer) {
                salesPayments.applyCleared(e.companyId(), invoiceId, f);
            } else {
                purchasePayments.applyCleared(e.companyId(), invoiceId, f);
            }
        }
    }

    private static Long id(JsonNode n) {
        String v = n == null ? "" : n.asText("");
        return v.matches("[0-9]{1,19}") ? Long.valueOf(v) : null;
    }
}
