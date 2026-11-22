package com.terp.erp.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.event.EEventRoute;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DNS is faked: hooks.example.com is public, internal.example.com resolves to a private address. */
class WebhookChannelTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OutboundHttp http = mock(OutboundHttp.class);
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName(
            host.startsWith("internal") ? "10.0.0.5" : "93.184.216.34")});
    private final WebhookChannel channel = new WebhookChannel(guard, http, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    private final EventEnvelope event = new EventEnvelope("e-1", "sales.invoice.posted", 7L, "SALES_INVOICE", "9",
            NOW, null, mapper.createObjectNode().put("number", "INV-2026-11-001"));

    @Test
    @SuppressWarnings("unchecked")
    void signsTheTimestampAndTheBody() {
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(true, 200, null));

        SendResult r = channel.send("https://hooks.example.com/erp", "a-long-signing-secret",
                EEventRoute.SALES_INVOICE_POSTED, event);

        assertThat(r.sent()).isTrue();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).postJson(eq(URI.create("https://hooks.example.com/erp")), body.capture(), headers.capture());
        String ts = String.valueOf(NOW.getEpochSecond());
        assertThat(headers.getValue()).containsEntry("X-ERP-Event", "NEW_SALES_INVOICE")
                .containsEntry("X-ERP-Timestamp", ts)
                .containsEntry("X-ERP-Signature",
                        "sha256=" + WebhookChannel.sign("a-long-signing-secret", ts + "." + body.getValue()));
        assertThat(body.getValue()).startsWith("{\"event\":\"NEW_SALES_INVOICE\",\"eventName\":\"New Sales Invoice\","
                + "\"module\":\"SALES\",\"eventId\":\"e-1\",\"companyId\":\"7\"")
                .contains("\"data\":{\"number\":\"INV-2026-11-001\"}");
    }

    @Test
    void theSignatureIsHmacSha256Hex() {
        // RFC 4231 test case 2.
        assertThat(WebhookChannel.sign("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnsignedWebhookHasNoSignature() {
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(true, 204, null));

        channel.send("https://hooks.example.com/erp", null, EEventRoute.SALES_INVOICE_POSTED, event);

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).postJson(any(), anyString(), headers.capture());
        assertThat(headers.getValue()).containsOnlyKeys("X-ERP-Event");
    }

    @Test
    void aRefusedUrlFailsWithACodeAndNeverCallsOut() {
        for (String url : new String[]{"http://hooks.example.com/erp", "https://internal.example.com/erp",
                "https://127.0.0.1/erp", "https://keycloak/realms"}) {
            SendResult r = channel.send(url, null, EEventRoute.SALES_INVOICE_POSTED, event);
            assertThat(r.sent()).isFalse();
            assertThat(r.code()).isEqualTo("URL_REFUSED");
            assertThat(r.retryable()).isFalse();
        }
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void serverErrorsAreRetriedClientErrorsAreNot() {
        assertThat(SendResult.ofHttp(new OutboundHttp.Result(false, 503, null)).retryable()).isTrue();
        assertThat(SendResult.ofHttp(new OutboundHttp.Result(false, null, "TIMEOUT")).code()).isEqualTo("TIMEOUT");
        assertThat(SendResult.ofHttp(new OutboundHttp.Result(false, 404, null)))
                .isEqualTo(SendResult.fail("HTTP_404"));
    }
}
