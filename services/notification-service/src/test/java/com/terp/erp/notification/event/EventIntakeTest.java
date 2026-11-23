package com.terp.erp.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.NotificationConfig;
import com.terp.erp.notification.client.IamInternalClient.Slack;
import com.terp.erp.notification.client.IamInternalClient.Smtp;
import com.terp.erp.notification.client.IamInternalClient.Webhook;
import com.terp.erp.notification.delivery.Delivery;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.delivery.DeliveryRepository;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventIntakeTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final IamInternalClient iam = mock(IamInternalClient.class);
    private final EventIntake intake = new EventIntake(processed, deliveries, iam, mapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Smtp smtp = new Smtp("smtp.example.com", 587, null, null, "TLS", "noreply@example.com", "Test Co");

    private EventEnvelope ticketCreated(String eventId) {
        ObjectNode data = mapper.createObjectNode().put("ticketNumber", "T-0001").put("subject", "Cannot log in")
                .put("name", "Test Customer").put("recipientEmail", "customer@example.invalid");
        return new EventEnvelope(eventId, "support.ticket.created", 7L, "TICKET", "1", NOW, null, data);
    }

    @SuppressWarnings("unchecked")
    private List<Delivery> savedRows() {
        ArgumentCaptor<Iterable<Delivery>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveries).saveAll(c.capture());
        List<Delivery> out = new ArrayList<>();
        c.getValue().forEach(out::add);
        return out;
    }

    @Test
    void theSameEventTwiceCreatesOneDeliverySet() {
        Set<String> seen = new HashSet<>();
        when(processed.existsById(anyString())).thenAnswer(i -> seen.contains(i.<String>getArgument(0)));
        when(processed.saveAndFlush(any())).thenAnswer(i -> {
            seen.add(i.<ProcessedEvent>getArgument(0).getEventId());
            return i.getArgument(0);
        });
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null))
                .thenReturn(new NotificationConfig("7", "en", true, smtp, null, null, null, List.of(), null, null, null));

        intake.accept(ticketCreated("e-1"));
        List<Long> second = intake.accept(ticketCreated("e-1"));

        assertThat(second).isEmpty();
        verify(deliveries, times(1)).saveAll(anyIterable());
        verify(iam, times(1)).config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null);
        assertThat(savedRows()).singleElement().satisfies(d -> {
            assertThat(d.getChannel()).isEqualTo(EChannel.EMAIL);
            assertThat(d.getStatus()).isEqualTo(EStatus.PENDING);
            assertThat(d.getTarget()).isEqualTo("*@example.invalid");
            assertThat(d.getRecipientRef()).isEqualTo(EventIntake.PAYLOAD_RECIPIENT);
        });
    }

    @Test
    void aSwitchedOffEmailIsSkipped() {
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null))
                .thenReturn(new NotificationConfig("7", "vi", false, null, null, null, null, List.of(), null, null, null));

        assertThat(intake.accept(ticketCreated("e-2"))).isEmpty();

        assertThat(savedRows()).singleElement().satisfies(d -> {
            assertThat(d.getChannel()).isEqualTo(EChannel.EMAIL);
            assertThat(d.getStatus()).isEqualTo(EStatus.SKIPPED);
            assertThat(d.getLastErrorCode()).isEqualTo("EMAIL_DISABLED");
        });
    }

    @Test
    void aDisabledIntegrationGetsNoRow() {
        // IAM answers slack = null when the integration is off; telegram on is the control.
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null)).thenReturn(new NotificationConfig("7", "en", true,
                smtp, null, new IamInternalClient.Telegram("123456:abcdefghijklmnopqrstuvwxyz", "42"), null, List.of(), null, null, null));

        intake.accept(ticketCreated("e-3"));

        assertThat(savedRows()).extracting(Delivery::getChannel).containsExactly(EChannel.EMAIL, EChannel.TELEGRAM);
    }

    @Test
    void slackAndWebhooksOfTheMappedEventGetARowEach() {
        ObjectNode data = mapper.createObjectNode().put("number", "INV-2026-11-001");
        EventEnvelope e = new EventEnvelope("e-4", "sales.invoice.posted", 7L, "SALES_INVOICE", "9", NOW, null, data);
        when(iam.config(7L, "sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE")).thenReturn(new NotificationConfig("7", "en", true,
                smtp, new Slack("https://hooks.slack.com/services/x"), null, null,
                List.of(new Webhook("3", "https://hooks.example.com/erp", null)), null, null, null));

        intake.accept(e);

        List<Delivery> rows = savedRows();
        assertThat(rows).extracting(Delivery::getChannel)
                .containsExactly(EChannel.EMAIL, EChannel.SLACK, EChannel.WEBHOOK);
        assertThat(rows.get(0).getLastErrorCode()).isEqualTo("NO_RECIPIENT");
        assertThat(rows.get(2).getWebhookId()).isEqualTo(3L);
        assertThat(rows.get(2).getTarget()).isEqualTo("hooks.example.com");
    }

    @Test
    void assigneesAreResolvedLaterOnePerUser() {
        ObjectNode data = mapper.createObjectNode().put("name", "Test Prospect");
        data.putArray("recipientUserIds").add("12").add("12").add("13");
        EventEnvelope e = new EventEnvelope("e-5", "crm.lead.assigned", 7L, "LEAD", "4", NOW, null, data);
        when(iam.config(7L, "crm.lead.assigned", "LEAD_ASSIGNED", null))
                .thenReturn(new NotificationConfig("7", "en", true, smtp, null, null, null, List.of(), null, null, null));

        intake.accept(e);

        assertThat(savedRows()).extracting(Delivery::getRecipientRef).containsExactly("user:12", "user:13");
    }

    @Test
    void aChatChannelSwitchedOffForTheEventGetsASkippedRow() {
        ObjectNode data = mapper.createObjectNode().put("number", "INV-2026-11-001")
                .put("recipientPhone", "+15005550006");
        EventEnvelope e = new EventEnvelope("e-7", "sales.invoice.posted", 7L, "SALES_INVOICE", "9", NOW, null, data);
        when(iam.config(7L, "sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE")).thenReturn(
                new NotificationConfig("7", "en", true, smtp, new Slack("https://hooks.slack.com/services/x"),
                        new IamInternalClient.Telegram("123456:abcdefghijklmnopqrstuvwxyz", "42"),
                        new IamInternalClient.Twilio("AC" + "0".repeat(32), "token", "+15005550001"), List.of(),
                        false, true, false));

        List<Long> pending = intake.accept(e);

        List<Delivery> rows = savedRows();
        assertThat(rows).extracting(Delivery::getChannel)
                .containsExactly(EChannel.EMAIL, EChannel.SLACK, EChannel.TELEGRAM, EChannel.SMS);
        assertThat(rows.get(1).getStatus()).isEqualTo(EStatus.SKIPPED);
        assertThat(rows.get(1).getLastErrorCode()).isEqualTo("CHANNEL_EVENT_DISABLED");
        assertThat(rows.get(2).getStatus()).isEqualTo(EStatus.PENDING);
        assertThat(rows.get(3).getStatus()).isEqualTo(EStatus.SKIPPED);
        assertThat(rows.get(3).getLastErrorCode()).isEqualTo("CHANNEL_EVENT_DISABLED");
        assertThat(rows.get(3).getTarget()).isEqualTo("*006");
        assertThat(pending).hasSize(1);
    }

    @Test
    void smsNeedsAnE164PhoneInTheEvent() {
        ObjectNode data = mapper.createObjectNode().put("number", "INV-1").put("recipientPhone", "0901234567");
        EventEnvelope e = new EventEnvelope("e-8", "sales.invoice.posted", 7L, "SALES_INVOICE", "9", NOW, null, data);
        when(iam.config(7L, "sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE")).thenReturn(
                new NotificationConfig("7", "en", true, smtp, null, null,
                        new IamInternalClient.Twilio("AC" + "0".repeat(32), "token", "+15005550001"), List.of(),
                        null, null, null));

        intake.accept(e);

        assertThat(savedRows()).extracting(Delivery::getChannel).containsExactly(EChannel.EMAIL);
    }

    @Test
    void anUnknownEventTypeIsRecordedAndIgnored() {
        EventEnvelope e = new EventEnvelope("e-6", "inventory.stock.moved", 7L, "STOCK", "1", NOW, null,
                mapper.createObjectNode());

        assertThat(intake.accept(e)).isEmpty();

        verify(processed).saveAndFlush(any());
        verify(deliveries, times(0)).saveAll(anyIterable());
    }

    private static final IamInternalClient.GoogleCalendar GCAL =
            new IamInternalClient.GoogleCalendar("team@group.calendar.google.com", "{\"type\":\"service_account\"}");

    private NotificationConfig withCalendar(IamInternalClient.GoogleCalendar gcal) {
        return new NotificationConfig("7", "en", false, null, new Slack("https://hooks.slack.com/services/x"), null,
                null, List.of(), null, null, null, null, gcal);
    }

    @Test
    void aHolidayWithCalendarSyncGetsAGoogleCalendarRowWithAMaskedCalendarId() {
        ObjectNode data = mapper.createObjectNode().put("name", "Test Holiday").put("startDate", "2026-12-24")
                .put("endDate", "2026-12-24").put("calendarSync", true);
        when(iam.config(7L, "employee.holiday.created", null, "NEW_HOLIDAY")).thenReturn(withCalendar(GCAL));

        intake.accept(new EventEnvelope("g-1", "employee.holiday.created", 7L, "HOLIDAY", "12", NOW, null, data));

        List<Delivery> rows = savedRows();
        assertThat(rows).extracting(Delivery::getChannel).containsExactly(EChannel.SLACK, EChannel.GOOGLE_CALENDAR);
        assertThat(rows.get(1).getStatus()).isEqualTo(EStatus.PENDING);
        assertThat(rows.get(1).getTarget()).isEqualTo("team…@…");
    }

    @Test
    void noCalendarRowWhenTheFlagIsOffOrTheIntegrationIsOff() {
        ObjectNode off = mapper.createObjectNode().put("number", "INV-1").put("dueDate", "2026-12-31")
                .put("syncToGoogleCalendar", false);
        when(iam.config(7L, "sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE"))
                .thenReturn(withCalendar(GCAL));
        intake.accept(new EventEnvelope("g-2", "sales.invoice.posted", 7L, "SALES_INVOICE", "9", NOW, null, off));
        assertThat(savedRows()).extracting(Delivery::getChannel).doesNotContain(EChannel.GOOGLE_CALENDAR);
    }

    @Test
    void noCalendarRowWhenTheIntegrationIsOff() {
        ObjectNode on = mapper.createObjectNode().put("number", "INV-1").put("dueDate", "2026-12-31")
                .put("syncToGoogleCalendar", true);
        when(iam.config(7L, "sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE"))
                .thenReturn(withCalendar(null));
        intake.accept(new EventEnvelope("g-3", "sales.invoice.posted", 7L, "SALES_INVOICE", "9", NOW, null, on));
        assertThat(savedRows()).extracting(Delivery::getChannel).doesNotContain(EChannel.GOOGLE_CALENDAR);
    }

    @Test
    void aHolidayUpdateIsForTheCalendarOnly() {
        ObjectNode data = mapper.createObjectNode().put("name", "Test Holiday").put("startDate", "2026-12-24")
                .put("endDate", "2026-12-24").put("calendarSync", true);
        when(iam.config(7L, "employee.holiday.updated", null, null)).thenReturn(withCalendar(GCAL));

        intake.accept(new EventEnvelope("g-4", "employee.holiday.updated", 7L, "HOLIDAY", "12", NOW, null, data));

        assertThat(savedRows()).extracting(Delivery::getChannel).containsExactly(EChannel.GOOGLE_CALENDAR);
    }
}
