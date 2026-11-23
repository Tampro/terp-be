package com.terp.erp.notification.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.channel.ChatChannel;
import com.terp.erp.notification.channel.EmailChannel;
import com.terp.erp.notification.channel.SendResult;
import com.terp.erp.notification.channel.WebhookChannel;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.NotificationConfig;
import com.terp.erp.notification.client.IamInternalClient.Smtp;
import com.terp.erp.notification.config.NotificationProperties;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.event.ProcessedEvent;
import com.terp.erp.notification.event.ProcessedEventRepository;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final IamInternalClient iam = mock(IamInternalClient.class);
    private final EmailChannel email = mock(EmailChannel.class);
    private final OutboundHttp http = mock(OutboundHttp.class);
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("10.1.1.1")});
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final NotificationProperties props = new NotificationProperties("http://iam", "erp.events", "q", "dlq",
            3, Duration.ofMinutes(1), Duration.ofMinutes(10), 50);
    private final DeliveryDispatcher dispatcher = new DeliveryDispatcher(deliveries, processed, iam, email,
            new ChatChannel(guard, http, mapper), new WebhookChannel(guard, http, mapper, clock),
            new com.terp.erp.notification.channel.SmsChannel(guard,
                    mock(com.terp.erp.notification.channel.FormHttp.class)), props, mapper,
            clock);
    private final Smtp smtp = new Smtp("smtp.example.com", 587, null, null, "TLS", "noreply@example.com", null);

    private Delivery delivery(long id, EChannel channel) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setCompanyId(7L);
        d.setEventId("e-1");
        d.setEventType("support.ticket.created");
        d.setChannel(channel);
        d.setStatus(EStatus.PENDING);
        when(deliveries.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @BeforeEach
    void event() throws Exception {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId("e-1");
        pe.setEnvelope(mapper.writeValueAsString(new EventEnvelope("e-1", "support.ticket.created", 7L, "TICKET",
                "1", NOW, null, mapper.createObjectNode().put("ticketNumber", "T-0001")
                .put("subject", "Cannot log in").put("recipientEmail", "customer@example.invalid"))));
        when(processed.findById("e-1")).thenReturn(Optional.of(pe));
    }

    @Test
    void anEmailIsSentInTheCompanyLanguage() {
        Delivery d = delivery(1, EChannel.EMAIL);
        d.setRecipientRef("payload");
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null))
                .thenReturn(new NotificationConfig("7", "en", true, smtp, null, null, null, List.of(),
                        null, null, null));
        when(email.send(any(), anyString(), anyString(), anyString())).thenReturn(SendResult.ok());

        dispatcher.dispatch(List.of(1L));

        verify(email).send(eq(smtp), eq("customer@example.invalid"), eq("Ticket T-0001: Cannot log in"),
                anyString());
        assertThat(d.getStatus()).isEqualTo(EStatus.SENT);
        assertThat(d.getAttempts()).isEqualTo(1);
        assertThat(d.getTarget()).isEqualTo("*@example.invalid");
    }

    @Test
    void aSwitchTurnedOffSinceTheEventSkipsTheEmail() {
        Delivery d = delivery(1, EChannel.EMAIL);
        d.setRecipientRef("payload");
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null))
                .thenReturn(new NotificationConfig("7", "en", false, null, null, null, null, List.of(),
                        null, null, null));

        dispatcher.dispatch(List.of(1L));

        assertThat(d.getStatus()).isEqualTo(EStatus.SKIPPED);
        assertThat(d.getLastErrorCode()).isEqualTo("EMAIL_DISABLED");
        verify(email, never()).send(any(), anyString(), anyString(), anyString());
    }

    @Test
    void aRetryableFailureBacksOffThenFails() {
        Delivery d = delivery(1, EChannel.EMAIL);
        d.setRecipientRef("payload");
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null))
                .thenReturn(new NotificationConfig("7", "vi", true, smtp, null, null, null, List.of(),
                        null, null, null));
        when(email.send(any(), anyString(), anyString(), anyString())).thenReturn(SendResult.retry("SMTP_ERROR"));

        dispatcher.dispatch(List.of(1L));
        assertThat(d.getStatus()).isEqualTo(EStatus.PENDING);
        assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        dispatcher.dispatch(List.of(1L));
        assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        dispatcher.dispatch(List.of(1L));

        assertThat(d.getStatus()).isEqualTo(EStatus.FAILED);
        assertThat(d.getAttempts()).isEqualTo(3);
        assertThat(d.getLastErrorCode()).isEqualTo("SMTP_ERROR");
    }

    @Test
    void aSlackUrlRefusedByTheGuardFailsWithoutAnyCall() {
        Delivery d = delivery(2, EChannel.SLACK);
        d.setEventType("support.ticket.created");
        // The fake DNS resolves every name to 10.1.1.1 (private): the guard refuses before any connection.
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null)).thenReturn(
                new NotificationConfig("7", "en", true, smtp, new IamInternalClient.Slack(
                        "https://hooks.slack.com/services/x"), null, null, List.of(), null, null, null));

        dispatcher.dispatch(List.of(2L));

        assertThat(d.getStatus()).isEqualTo(EStatus.FAILED);
        assertThat(d.getLastErrorCode()).isEqualTo("URL_REFUSED");
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void iamDownKeepsTheDeliveryPending() {
        Delivery d = delivery(1, EChannel.EMAIL);
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null)).thenThrow(
                new com.terp.erp.exception.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                com.terp.erp.notification.common.ENotificationError.NTF_004));

        dispatcher.dispatch(List.of(1L));

        assertThat(d.getStatus()).isEqualTo(EStatus.PENDING);
        assertThat(d.getLastErrorCode()).isEqualTo("IAM_UNAVAILABLE");
        verify(deliveries).save(d);
    }

    @Test
    void aChatChannelSwitchedOffForTheEventSinceIntakeIsSkipped() {
        Delivery d = delivery(2, EChannel.SLACK);
        when(iam.config(7L, "support.ticket.created", "SUPPORT_TICKET_CREATED", null)).thenReturn(
                new NotificationConfig("7", "en", true, smtp, new IamInternalClient.Slack(
                        "https://hooks.slack.com/services/x"), null, null, List.of(), false, null, null));

        dispatcher.dispatch(List.of(2L));

        assertThat(d.getStatus()).isEqualTo(EStatus.SKIPPED);
        assertThat(d.getLastErrorCode()).isEqualTo("CHANNEL_EVENT_DISABLED");
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }
}
