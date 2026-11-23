package com.terp.erp.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.client.IamInternalClient.GoogleCalendar;
import com.terp.erp.notification.delivery.Targets;
import com.terp.erp.notification.event.EEventRoute.ECalendar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCalendarChannelTest {
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final String CALENDAR = "team@group.calendar.google.com";
    private static final String EVENTS = "https://www.googleapis.com/calendar/v3/calendars/"
            + "team%40group.calendar.google.com/events";
    private static final String TOKEN = "ya29.test-access-token";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final GoogleHttp http = mock(GoogleHttp.class);
    /** Fake DNS: every name resolves to a public documentation address; nothing is called for real. */
    private final UrlGuard publicDns = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("203.0.113.10")});
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final GoogleCalendarChannel channel = new GoogleCalendarChannel(publicDns, http, mapper, clock);
    /** A throwaway key generated per test run: never a real service account key. */
    private final KeyPair keys = newKeys();
    private final GoogleCalendar cfg = new GoogleCalendar(CALENDAR, keyFile(keys, "erp-sync@test-project.iam.gserviceaccount.com"));

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String keyFile(KeyPair kp, String email) {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        return mapper.createObjectNode().put("type", "service_account").put("project_id", "test-project")
                .put("client_email", email).put("private_key", pem).toString();
    }

    private ObjectNode holiday() {
        return mapper.createObjectNode().put("holidayId", "12").put("name", "Test Holiday")
                .put("startDate", "2026-12-24").put("endDate", "2026-12-25").put("calendarSync", true);
    }

    private ObjectNode invoice() {
        return mapper.createObjectNode().put("invoiceId", "34").put("number", "INV-000034")
                .put("customerName", "Test Customer Co").put("dueDate", "2026-12-31").put("total", 1250000.5000)
                .put("syncToGoogleCalendar", true);
    }

    private void tokenOk() {
        when(http.postFormForBody(any(), anyString())).thenReturn(new GoogleHttp.Response(200,
                "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":3599,\"token_type\":\"Bearer\"}", null));
    }

    private void calendarAnswers(int status) {
        when(http.send(anyString(), any(), any(), anyMap())).thenReturn(new GoogleHttp.Response(status, null, null));
    }

    @Test
    void theAssertionHasTheServiceAccountClaimsAndVerifiesWithItsPublicKey() throws Exception {
        tokenOk();
        calendarAnswers(200);

        channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday());

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<String> form = ArgumentCaptor.forClass(String.class);
        verify(http).postFormForBody(uri.capture(), form.capture());
        assertThat(uri.getValue().toString()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(form.getValue()).startsWith("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
                + "&assertion=");
        String jwt = URLDecoder.decode(form.getValue().substring(form.getValue().indexOf("assertion=") + 10),
                StandardCharsets.UTF_8);
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        Base64.Decoder d = Base64.getUrlDecoder();
        JsonNode header = mapper.readTree(d.decode(parts[0]));
        JsonNode claims = mapper.readTree(d.decode(parts[1]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(claims.path("iss").asText()).isEqualTo("erp-sync@test-project.iam.gserviceaccount.com");
        assertThat(claims.path("scope").asText()).isEqualTo("https://www.googleapis.com/auth/calendar.events");
        assertThat(claims.path("aud").asText()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(claims.path("iat").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(3600);
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(keys.getPublic());
        v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(v.verify(d.decode(parts[2]))).isTrue();
    }

    @Test
    void theTokenIsCachedPerCompanyUntilAMinuteBeforeItExpires() {
        tokenOk();
        calendarAnswers(200);

        channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday());
        channel.send(7L, cfg, ECalendar.INVOICE_DUE, "34", invoice());
        verify(http, times(1)).postFormForBody(any(), anyString());

        now.set(NOW.plusSeconds(3599 - 59));
        channel.send(7L, cfg, ECalendar.INVOICE_DUE, "34", invoice());
        verify(http, times(2)).postFormForBody(any(), anyString());

        // another company never reuses this one's token; a replaced key file neither
        channel.send(8L, cfg, ECalendar.INVOICE_DUE, "34", invoice());
        channel.send(7L, new GoogleCalendar(CALENDAR, keyFile(newKeys(), "other@test-project.iam.gserviceaccount.com")),
                ECalendar.INVOICE_DUE, "34", invoice());
        verify(http, times(4)).postFormForBody(any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aHolidayIsAnAllDayEventWithAnExclusiveEndAndADeterministicId() throws Exception {
        tokenOk();
        calendarAnswers(200);

        SendResult r = channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday());

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).send(eq("POST"), uri.capture(), body.capture(), headers.capture());
        assertThat(r).isEqualTo(SendResult.ok());
        assertThat(uri.getValue().toString()).isEqualTo(EVENTS);
        assertThat(headers.getValue()).containsEntry("Authorization", "Bearer " + TOKEN);
        JsonNode e = mapper.readTree(body.getValue());
        assertThat(e.path("id").asText()).isEqualTo("erp7hol12").matches("[0-9a-v]{5,1024}");
        assertThat(e.path("summary").asText()).isEqualTo("Test Holiday");
        assertThat(e.path("start").path("date").asText()).isEqualTo("2026-12-24");
        assertThat(e.path("end").path("date").asText()).isEqualTo("2026-12-26");
        assertThat(GoogleCalendarChannel.eventId(7L, ECalendar.HOLIDAY_UPDATE, "12")).isEqualTo("erp7hol12");
    }

    @Test
    void anInvoiceIsItsDueDateWithTheCustomerAndTotal() throws Exception {
        tokenOk();
        calendarAnswers(200);

        channel.send(7L, cfg, ECalendar.INVOICE_DUE, "34", invoice());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(http).send(eq("POST"), any(), body.capture(), anyMap());
        JsonNode e = mapper.readTree(body.getValue());
        assertThat(e.path("id").asText()).isEqualTo("erp7inv34").matches("[0-9a-v]{5,1024}");
        assertThat(e.path("summary").asText()).isEqualTo("Invoice INV-000034 due — Test Customer Co");
        assertThat(e.path("description").asText()).contains("1250000.5");
        assertThat(e.path("start").path("date").asText()).isEqualTo("2026-12-31");
        assertThat(e.path("end").path("date").asText()).isEqualTo("2027-01-01");
    }

    @Test
    void anEntryThatAlreadyExistsIsSent() {
        tokenOk();
        calendarAnswers(409);

        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday())).isEqualTo(SendResult.ok());
    }

    @Test
    void anUpdateOfAMissingEntryCreatesItAndADeleteOfAMissingOneIsDone() {
        tokenOk();
        when(http.send(eq("PUT"), any(), anyString(), anyMap())).thenReturn(new GoogleHttp.Response(404, null, null));
        when(http.send(eq("POST"), any(), anyString(), anyMap())).thenReturn(new GoogleHttp.Response(200, null, null));
        when(http.send(eq("DELETE"), any(), isNull(), anyMap())).thenReturn(new GoogleHttp.Response(410, null, null));

        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_UPDATE, "12", holiday())).isEqualTo(SendResult.ok());
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_DELETE, "12", holiday())).isEqualTo(SendResult.ok());

        verify(http).send(eq("PUT"), eq(URI.create(EVENTS + "/erp7hol12")), anyString(), anyMap());
        verify(http).send(eq("POST"), eq(URI.create(EVENTS)), anyString(), anyMap());
        verify(http).send(eq("DELETE"), eq(URI.create(EVENTS + "/erp7hol12")), isNull(), anyMap());
    }

    @Test
    void anUnusableKeyFileOrCalendarIdIsInvalidConfigWithoutAnyCall() {
        for (GoogleCalendar bad : new GoogleCalendar[]{
                new GoogleCalendar(CALENDAR, "not json"),
                new GoogleCalendar(CALENDAR, "{\"type\":\"service_account\",\"client_email\":\"a@b.c\","
                        + "\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nAAAA\\n-----END PRIVATE KEY-----\"}"),
                new GoogleCalendar(CALENDAR, "{\"type\":\"authorized_user\"}"),
                new GoogleCalendar("../../users/me", cfg.serviceAccountJson()),
                new GoogleCalendar(" ", cfg.serviceAccountJson()),
                null}) {
            assertThat(channel.send(7L, bad, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                    .isEqualTo(SendResult.fail("INVALID_CONFIG"));
        }
        verify(http, never()).postFormForBody(any(), anyString());
        verify(http, never()).send(anyString(), any(), any(), anyMap());
    }

    @Test
    void googleHostsResolvingToAPrivateAddressAreRefused() {
        UrlGuard privateDns = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("10.0.0.5")});

        SendResult r = new GoogleCalendarChannel(privateDns, http, mapper, clock)
                .send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday());

        assertThat(r).isEqualTo(SendResult.fail("URL_REFUSED"));
        verify(http, never()).postFormForBody(any(), anyString());
        verify(http, never()).send(anyString(), any(), any(), anyMap());
    }

    @Test
    void answersAreMappedToCodesAndA401DropsTheCachedToken() {
        tokenOk();
        calendarAnswers(401);
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.fail("AUTH_FAILED"));
        calendarAnswers(403);
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.fail("CALENDAR_FORBIDDEN"));
        verify(http, times(2)).postFormForBody(any(), anyString()); // the 401 made the next send fetch a new token
        calendarAnswers(404);
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.fail("CALENDAR_NOT_FOUND"));
        calendarAnswers(503);
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.retry("HTTP_503"));
        calendarAnswers(429);
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()).retryable()).isTrue();
    }

    @Test
    void aRefusedTokenIsFinalAndAnUnavailableTokenEndpointIsRetried() {
        when(http.postFormForBody(any(), anyString())).thenReturn(new GoogleHttp.Response(400,
                "{\"error\":\"invalid_grant\"}", null));
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.fail("AUTH_FAILED"));
        when(http.postFormForBody(any(), anyString())).thenReturn(new GoogleHttp.Response(null, null, "TIMEOUT"));
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", holiday()))
                .isEqualTo(SendResult.retry("TOKEN_TIMEOUT"));
        verify(http, never()).send(anyString(), any(), any(), anyMap());
    }

    @Test
    void aPayloadWithoutDatesOrANumericSourceIdIsInvalidEvent() {
        tokenOk();
        assertThat(channel.send(7L, cfg, ECalendar.HOLIDAY_CREATE, "12", mapper.createObjectNode().put("name", "X")))
                .isEqualTo(SendResult.fail("INVALID_EVENT"));
        assertThat(channel.send(7L, cfg, ECalendar.INVOICE_DUE, "x/../1", invoice()))
                .isEqualTo(SendResult.fail("INVALID_EVENT"));
        verify(http, never()).send(anyString(), any(), any(), anyMap());
    }

    @Test
    void secretsNeverShowInToStringsOrTheDeliveryTarget() {
        assertThat(cfg.toString()).doesNotContain("PRIVATE KEY").doesNotContain("client_email");
        assertThat(new GoogleCalendarChannel.CachedToken("f", TOKEN, NOW).toString()).doesNotContain(TOKEN);
        assertThat(new GoogleHttp.Response(200, "{\"access_token\":\"" + TOKEN + "\"}", null).toString())
                .doesNotContain(TOKEN);
        assertThat(channel.serviceAccount(cfg.serviceAccountJson()).toString()).doesNotContain("PRIVATE");
        assertThat(Targets.maskCalendarId(CALENDAR)).isEqualTo("team…@…");
    }
}
