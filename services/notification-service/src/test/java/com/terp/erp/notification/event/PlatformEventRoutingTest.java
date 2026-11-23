package com.terp.erp.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.TestLogin;
import com.terp.erp.notification.channel.ChatChannel;
import com.terp.erp.notification.channel.EmailChannel;
import com.terp.erp.notification.channel.SendResult;
import com.terp.erp.notification.channel.WebhookChannel;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.PlatformConfig;
import com.terp.erp.notification.client.IamInternalClient.Smtp;
import com.terp.erp.notification.client.IamInternalClient.Webhook;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.config.NotificationProperties;
import com.terp.erp.notification.delivery.Delivery;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.delivery.DeliveryDispatcher;
import com.terp.erp.notification.delivery.DeliveryRepository;
import com.terp.erp.notification.delivery.DeliveryService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Platform events (companyId null) reach the platform's webhooks and team addresses, never a company's. */
class PlatformEventRoutingTest {
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final IamInternalClient iam = mock(IamInternalClient.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EventIntake intake = new EventIntake(processed, deliveries, iam, mapper, clock,
            mock(com.terp.erp.notification.feed.FeedWriter.class));
    private final Smtp smtp = new Smtp("smtp.example.com", 587, null, null, "TLS", "noreply@example.com", null);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private EventEnvelope platformTicket(String eventId) {
        return new EventEnvelope(eventId, "platform.helpdesk.ticket_created", null, "HELPDESK_TICKET", "12", NOW,
                null, mapper.createObjectNode().put("ticketNumber", "00000012").put("subject", "Cannot log in")
                .put("companyId", "10").put("companyName", "Test Company"));
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
    void aPlatformEventUsesOnlyThePlatformConfigAndKeepsCompanyNull() {
        when(iam.platformConfig("HELPDESK_TICKET_CREATED", "NEW_HELPDESK_TICKET")).thenReturn(new PlatformConfig(
                "en", true, smtp, List.of("support@example.com", "ops@example.org"),
                List.of(new Webhook("3", "https://hooks.example.com/erp", null))));

        intake.accept(platformTicket("p-1"));

        verify(iam, never()).config(any(), any(), any(), any());
        List<Delivery> rows = savedRows();
        assertThat(rows).hasSize(3).allSatisfy(d -> {
            assertThat(d.getCompanyId()).isNull();
            assertThat(d.getStatus()).isEqualTo(EStatus.PENDING);
        });
        assertThat(rows).filteredOn(d -> d.getChannel() == EChannel.EMAIL).extracting(Delivery::getTarget)
                .containsExactly("*@example.com", "*@example.org");
        assertThat(rows).filteredOn(d -> d.getChannel() == EChannel.EMAIL).allSatisfy(d ->
                assertThat(d.getRecipientRef()).startsWith("platform:").doesNotContain("@"));
        assertThat(rows).filteredOn(d -> d.getChannel() == EChannel.WEBHOOK).singleElement()
                .satisfies(d -> assertThat(d.getWebhookId()).isEqualTo(3L));
    }

    @Test
    void platformSwitchOffLeavesOneSkippedEmailRow() {
        when(iam.platformConfig("HELPDESK_TICKET_CREATED", "NEW_HELPDESK_TICKET"))
                .thenReturn(new PlatformConfig("en", false, null, List.of(), List.of()));

        intake.accept(platformTicket("p-2"));

        assertThat(savedRows()).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(EStatus.SKIPPED);
            assertThat(d.getLastErrorCode()).isEqualTo("EMAIL_DISABLED");
        });
    }

    @Test
    void aCompanyEventWithAPlatformTypeOrAnUnknownPlatformTypeIsIgnored() {
        EventEnvelope spoofed = new EventEnvelope("p-3", "platform.helpdesk.ticket_created", 7L, "HELPDESK_TICKET",
                "12", NOW, null, mapper.createObjectNode());
        EventEnvelope unknown = new EventEnvelope("p-4", "platform.nothing", null, "X", "1", NOW, null,
                mapper.createObjectNode());

        assertThat(intake.accept(spoofed)).isEmpty();
        assertThat(intake.accept(unknown)).isEmpty();

        verify(iam, never()).config(anyLong(), any(), any(), any());
        verify(iam, never()).platformConfig(any(), any());
        verify(deliveries, never()).saveAll(any());
    }

    @Test
    void aPlatformWebhookToAnUnresolvableHostFailsAsUrlRefusedAndEmailGoesToTheListedAddress() throws Exception {
        UrlGuard guard = new UrlGuard(host -> {
            throw new UnknownHostException(host);
        });
        EmailChannel email = mock(EmailChannel.class);
        when(email.send(any(), anyString(), anyString(), anyString())).thenReturn(SendResult.ok());
        OutboundHttp http = mock(OutboundHttp.class);
        DeliveryDispatcher dispatcher = new DeliveryDispatcher(deliveries, processed, iam, email,
                new ChatChannel(guard, http, mapper), new WebhookChannel(guard, http, mapper, clock),
                new com.terp.erp.notification.channel.SmsChannel(guard,
                        mock(com.terp.erp.notification.channel.FormHttp.class)),
                mock(com.terp.erp.notification.channel.GoogleCalendarChannel.class),
                new NotificationProperties("http://iam", "erp.events", "q", "dlq", 3, Duration.ofMinutes(1),
                        Duration.ofMinutes(10), 50), mapper, clock);
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId("p-5");
        pe.setEnvelope(mapper.writeValueAsString(platformTicket("p-5")));
        when(processed.findById("p-5")).thenReturn(Optional.of(pe));
        when(iam.platformConfig("HELPDESK_TICKET_CREATED", "NEW_HELPDESK_TICKET")).thenReturn(new PlatformConfig(
                "en", true, smtp, List.of("support@example.com"),
                List.of(new Webhook("3", "https://nowhere.invalid/hook", null))));
        Delivery hook = row(1L, EChannel.WEBHOOK);
        hook.setWebhookId(3L);
        Delivery mail = row(2L, EChannel.EMAIL);
        mail.setRecipientRef(EventIntake.platformRef("support@example.com"));

        dispatcher.dispatch(List.of(1L, 2L));

        assertThat(hook.getStatus()).isEqualTo(EStatus.FAILED);
        assertThat(hook.getLastErrorCode()).isEqualTo("URL_REFUSED");
        verify(http, never()).postJson(any(), any(), any());
        assertThat(mail.getStatus()).isEqualTo(EStatus.SENT);
        verify(email).send(eq(smtp), eq("support@example.com"), contains("00000012"), contains("Test Company"));
        verify(iam, never()).config(any(), any(), any(), any());
    }

    private static String contains(String s) {
        return org.mockito.ArgumentMatchers.contains(s);
    }

    private Delivery row(long id, EChannel channel) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setCompanyId(null);
        d.setEventId("p-5");
        d.setEventType("platform.helpdesk.ticket_created");
        d.setChannel(channel);
        d.setStatus(EStatus.PENDING);
        when(deliveries.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void platformDeliveriesAreListedToSuperAdminsOnly() {
        DeliveryService service = new DeliveryService(deliveries, mock(DeliveryDispatcher.class), clock);
        when(deliveries.searchPlatform(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        TestLogin.as(7L, "NOTIFICATION_LOG_VIEW");
        assertThatThrownBy(() -> service.list(true, null, null, null, null, null, null, Pageable.unpaged()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ENotificationError.NTF_005));
        Delivery platformRow = row(9L, EChannel.WEBHOOK);
        assertThatThrownBy(() -> service.get(9L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ENotificationError.NTF_002));

        TestLogin.superAdmin();
        service.list(true, null, null, null, null, null, null, Pageable.unpaged());
        verify(deliveries).searchPlatform(isNull(), isNull(), isNull(), isNull(), isNull(), any());
        assertThat(service.get(9L).companyId()).isNull();
        assertThat(platformRow.getCompanyId()).isNull();
    }
}
