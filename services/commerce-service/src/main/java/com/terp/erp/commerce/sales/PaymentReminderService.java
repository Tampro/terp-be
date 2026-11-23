package com.terp.erp.commerce.sales;

import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * accounting.invoice.payment_reminder (email PAYMENT_REMINDER to the customer): once per overdue posted / partly paid
 * sales invoice with a balance, per day. Runs in commerce because the invoices, due dates and balances live here;
 * the customer's email is the copy taken when the invoice was saved (a job has no user token, so no call to
 * accounting-service). Invoices saved before that copy existed have no email: the event is still published and
 * notification-service records it without a recipient.
 */
@Service
@RequiredArgsConstructor
public class PaymentReminderService {
    static final String EVENT = "accounting.invoice.payment_reminder";
    private static final List<EDocumentStatus> OPEN = List.of(EDocumentStatus.POSTED, EDocumentStatus.PARTIAL);

    private final SalesDocumentRepository documents;
    private final PaymentReminderLogRepository log;
    private final OutboxPublisher outbox;
    private final Clock clock;

    /** @return the number of reminders published for {@code today} */
    @Transactional
    public int remind(LocalDate today) {
        int sent = 0;
        for (SalesDocument d : documents.overdueInvoices(OPEN, today)) {
            if (log.existsByInvoiceIdAndReminderDate(d.getId(), today)) {
                continue;
            }
            PaymentReminderLog row = new PaymentReminderLog();
            row.setCompanyId(d.getCompanyId());
            row.setInvoiceId(d.getId());
            row.setReminderDate(today);
            row.setCreatedAt(Instant.now(clock));
            log.save(row);
            outbox.publish(d.getCompanyId(), EVENT, "SALES_INVOICE", d.getId(), Payload.of(
                    "invoiceId", d.getId(), "title", d.getNumber(), "number", d.getNumber(),
                    "customerId", d.getCustomerId(), "customerName", d.getCustomerName(),
                    "dueDate", d.getDueDate(), "daysOverdue", (int) ChronoUnit.DAYS.between(d.getDueDate(), today),
                    "total", d.getTotal(), "balance", d.getBalance(), "reminderDate", today,
                    "recipientEmail", d.getCustomerEmail()));
            sent++;
        }
        return sent;
    }
}
