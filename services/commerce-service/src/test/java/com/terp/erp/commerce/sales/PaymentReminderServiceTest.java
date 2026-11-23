package com.terp.erp.commerce.sales;

import com.terp.erp.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReminderServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private final SalesDocumentRepository documents = mock(SalesDocumentRepository.class);
    private final PaymentReminderLogRepository log = mock(PaymentReminderLogRepository.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final PaymentReminderService service = new PaymentReminderService(documents, log, outbox,
            Clock.fixed(Instant.parse("2026-11-23T01:00:00Z"), ZoneOffset.UTC));

    private static SalesDocument overdue(Long id) {
        SalesDocument d = new SalesDocument();
        d.setId(id);
        d.setCompanyId(10L);
        d.setKind(EDocumentKind.INVOICE);
        d.setNumber("SI-2026-11-00" + id);
        d.setCustomerId(5L);
        d.setCustomerName("Test Customer");
        d.setCustomerEmail("buyer@example.com");
        d.setDocDate(TODAY.minusDays(40));
        d.setDueDate(TODAY.minusDays(10));
        d.setStatus(EDocumentStatus.PARTIAL);
        d.setTotal(new BigDecimal("500"));
        d.setBalance(new BigDecimal("200"));
        return d;
    }

    @Test
    void asksOnlyForOpenOverdueInvoicesAndPublishesOneReminderEach() {
        when(documents.overdueInvoices(any(), eq(TODAY))).thenReturn(List.of(overdue(1L)));

        assertThat(service.remind(TODAY)).isEqualTo(1);

        verify(documents).overdueInvoices(eq(List.of(EDocumentStatus.POSTED, EDocumentStatus.PARTIAL)), eq(TODAY));
        ArgumentCaptor<PaymentReminderLog> row = ArgumentCaptor.forClass(PaymentReminderLog.class);
        verify(log).save(row.capture());
        assertThat(row.getValue().getInvoiceId()).isEqualTo(1L);
        assertThat(row.getValue().getReminderDate()).isEqualTo(TODAY);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(eq(10L), eq("accounting.invoice.payment_reminder"), eq("SALES_INVOICE"), eq(1L),
                payload.capture());
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsEntry("invoiceId", "1").containsEntry("title", "SI-2026-11-001")
                .containsEntry("recipientEmail", "buyer@example.com").containsEntry("daysOverdue", 10)
                .containsKeys("dueDate", "balance", "customerName");
    }

    @Test
    void anInvoiceAlreadyRemindedTodayIsSkipped() {
        when(documents.overdueInvoices(any(), eq(TODAY))).thenReturn(List.of(overdue(1L)));
        when(log.existsByInvoiceIdAndReminderDate(1L, TODAY)).thenReturn(true);

        assertThat(service.remind(TODAY)).isZero();

        verify(log, never()).save(any());
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
    }
}
