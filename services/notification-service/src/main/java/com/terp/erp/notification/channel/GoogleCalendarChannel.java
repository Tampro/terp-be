package com.terp.erp.notification.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.client.IamInternalClient.GoogleCalendar;
import com.terp.erp.notification.event.EEventRoute.ECalendar;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Calendar entries for holidays (calendarSync) and sales invoice due dates (syncToGoogleCalendar), written with
 * the company's service account (JWT bearer grant, RFC 7523, signed RS256 with the JDK — no Google library):
 * <ol>
 *   <li>POST https://oauth2.googleapis.com/token, form grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer &amp;
 *   assertion=JWT{iss=client_email, scope=calendar.events, aud=token URL, iat, exp=iat+1h}; the access token is
 *   cached per company (and key) until one minute before it expires.</li>
 *   <li>https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events: POST (create; 409 = the entry already
 *   exists = sent), PUT …/events/{id} (update; 404 → create) or DELETE …/events/{id} (404 / 410 = already gone).</li>
 * </ol>
 * The event id is deterministic (erp + company + hol|inv + source id: base32hex characters only), so a retry or a
 * redelivered event never makes a second entry. Both hosts are pinned by {@link UrlGuard}. Never logs the key file,
 * the assertion, the token or a body; only short codes reach the delivery row: INVALID_CONFIG (key file or calendar
 * id unusable, no call made), INVALID_EVENT (payload without dates / ids), URL_REFUSED, AUTH_FAILED (token refused
 * or 401), CALENDAR_FORBIDDEN (403: calendar not shared with the service account), CALENDAR_NOT_FOUND (404),
 * TOKEN_* / HTTP_408 / 429 / 5xx / TIMEOUT / CONNECTION_ERROR retried, other HTTP_4xx final.
 */
@Component
public class GoogleCalendarChannel {
    static final String TOKEN_HOST = "oauth2.googleapis.com";
    static final String TOKEN_URL = "https://" + TOKEN_HOST + "/token";
    static final String API_HOST = "www.googleapis.com";
    static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    static final Duration TOKEN_LIFETIME = Duration.ofHours(1);
    static final Duration TOKEN_MARGIN = Duration.ofMinutes(1);
    private static final String GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /** fingerprint: hash of the key file, so a replaced key never reuses the old key's token. */
    record CachedToken(String fingerprint, String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "CachedToken[expiresAt=" + expiresAt + "]";
        }
    }

    /** The parts of the key file used here. */
    record ServiceAccount(String clientEmail, PrivateKey key, String fingerprint) {
        @Override
        public String toString() {
            return "ServiceAccount[clientEmail=" + clientEmail + "]";
        }
    }

    private final UrlGuard guard;
    private final GoogleHttp http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<Long, CachedToken> tokens = new ConcurrentHashMap<>();

    public GoogleCalendarChannel(UrlGuard guard, GoogleHttp http, ObjectMapper mapper, Clock clock) {
        this.guard = guard;
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
    }

    public SendResult send(Long companyId, GoogleCalendar cfg, ECalendar kind, String sourceId, JsonNode data) {
        if (cfg == null || !validCalendarId(cfg.calendarId())) {
            return SendResult.fail("INVALID_CONFIG");
        }
        ServiceAccount sa = serviceAccount(cfg.serviceAccountJson());
        if (sa == null) {
            return SendResult.fail("INVALID_CONFIG");
        }
        String id = eventId(companyId, kind, sourceId);
        ObjectNode body = kind == ECalendar.HOLIDAY_DELETE ? null : eventBody(kind, id, data);
        if (id == null || (kind != ECalendar.HOLIDAY_DELETE && body == null)) {
            return SendResult.fail("INVALID_EVENT");
        }
        URI events;
        URI one;
        URI tokenUri;
        try {
            String base = "https://" + API_HOST + "/calendar/v3/calendars/" + pathSegment(cfg.calendarId().trim())
                    + "/events";
            events = guard.check(base, API_HOST);
            one = guard.check(base + "/" + id, API_HOST);
            tokenUri = guard.check(TOKEN_URL, TOKEN_HOST);
        } catch (UrlGuard.RefusedException e) {
            return SendResult.fail("URL_REFUSED");
        }
        TokenOutcome t = token(companyId, sa, tokenUri);
        if (t.failure() != null) {
            return t.failure();
        }
        Map<String, String> auth = Map.of("Authorization", "Bearer " + t.token());
        GoogleHttp.Response r;
        switch (kind) {
            case HOLIDAY_DELETE -> {
                r = http.send("DELETE", one, null, auth);
                if (r.status() != null && (r.status() == 404 || r.status() == 410)) {
                    return SendResult.ok();
                }
            }
            case HOLIDAY_UPDATE -> {
                r = http.send("PUT", one, json(body), auth);
                if (r.status() != null && r.status() == 404) {
                    // Not in the calendar yet (sync switched on by this edit): create it.
                    r = http.send("POST", events, json(body), auth);
                }
            }
            default -> r = http.send("POST", events, json(body), auth);
        }
        if (r.status() != null && r.status() == 409 && kind != ECalendar.HOLIDAY_DELETE) {
            return SendResult.ok(); // the deterministic id already exists: an earlier try went through
        }
        return map(companyId, r);
    }

    private SendResult map(Long companyId, GoogleHttp.Response r) {
        if (r.ok()) {
            return SendResult.ok();
        }
        if (r.status() == null) {
            return SendResult.retry(r.failure() == null ? "CONNECTION_ERROR" : r.failure());
        }
        int s = r.status();
        if (s == 401) {
            tokens.remove(companyId);
            return SendResult.fail("AUTH_FAILED");
        }
        if (s == 403) {
            // TODO(assumption): Google also answers 403 for some rate limits; without reading the body it is final.
            return SendResult.fail("CALENDAR_FORBIDDEN");
        }
        if (s == 404) {
            return SendResult.fail("CALENDAR_NOT_FOUND");
        }
        return s == 408 || s == 429 || s >= 500 ? SendResult.retry("HTTP_" + s) : SendResult.fail("HTTP_" + s);
    }

    // ------------------------------------------------------------------ token

    private record TokenOutcome(String token, SendResult failure) {
        @Override
        public String toString() {
            return "TokenOutcome[failure=" + failure + "]";
        }
    }

    private TokenOutcome token(Long companyId, ServiceAccount sa, URI tokenUri) {
        Instant now = Instant.now(clock);
        CachedToken c = tokens.get(companyId);
        if (c != null && c.fingerprint().equals(sa.fingerprint()) && now.isBefore(c.expiresAt().minus(TOKEN_MARGIN))) {
            return new TokenOutcome(c.token(), null);
        }
        String assertion;
        try {
            assertion = jwt(sa, now);
        } catch (GeneralSecurityException e) {
            return new TokenOutcome(null, SendResult.fail("INVALID_CONFIG"));
        }
        GoogleHttp.Response r = http.postFormForBody(tokenUri, "grant_type=" + enc(GRANT) + "&assertion="
                + enc(assertion));
        if (r.status() == null) {
            return new TokenOutcome(null, SendResult.retry("TOKEN_" + (r.failure() == null ? "CONNECTION_ERROR"
                    : r.failure())));
        }
        int s = r.status();
        if (!r.ok()) {
            if (s == 408 || s == 429 || s >= 500) {
                return new TokenOutcome(null, SendResult.retry("TOKEN_HTTP_" + s));
            }
            // 400 invalid_grant / 401 invalid_client: wrong or revoked key (or a server clock far off).
            return new TokenOutcome(null, SendResult.fail("AUTH_FAILED"));
        }
        try {
            JsonNode n = mapper.readTree(r.body() == null ? "" : r.body());
            String token = n == null ? "" : n.path("access_token").asText("");
            long expiresIn = n == null ? 0 : n.path("expires_in").asLong(0);
            if (token.isBlank() || expiresIn <= 0) {
                return new TokenOutcome(null, SendResult.retry("TOKEN_INVALID_RESPONSE"));
            }
            tokens.put(companyId, new CachedToken(sa.fingerprint(), token, now.plusSeconds(expiresIn)));
            return new TokenOutcome(token, null);
        } catch (JsonProcessingException e) {
            return new TokenOutcome(null, SendResult.retry("TOKEN_INVALID_RESPONSE"));
        }
    }

    /** header.claims.signature, base64url without padding; signed SHA256withRSA (RS256). */
    String jwt(ServiceAccount sa, Instant now) throws GeneralSecurityException {
        ObjectNode header = mapper.createObjectNode().put("alg", "RS256").put("typ", "JWT");
        ObjectNode claims = mapper.createObjectNode()
                .put("iss", sa.clientEmail())
                .put("scope", SCOPE)
                .put("aud", TOKEN_URL)
                .put("iat", now.getEpochSecond())
                .put("exp", now.plus(TOKEN_LIFETIME).getEpochSecond());
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String signingInput = b64.encodeToString(json(header).getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(json(claims).getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(sa.key());
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64.encodeToString(sig.sign());
    }

    /** null when the key file is not a usable service account (no call is made then). */
    ServiceAccount serviceAccount(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode n = mapper.readTree(json);
            String email = n == null ? "" : n.path("client_email").asText("").trim();
            String pem = n == null ? "" : n.path("private_key").asText("");
            if (!"service_account".equals(n == null ? null : n.path("type").asText()) || email.isBlank()
                    || !email.contains("@") || !pem.contains("BEGIN PRIVATE KEY")) {
                return null;
            }
            String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
            byte[] h = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return new ServiceAccount(email, key, HexFormat.of().formatHex(h));
        } catch (JsonProcessingException | IllegalArgumentException | GeneralSecurityException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ event

    /** "erp{company}hol{id}" / "erp{company}inv{id}": digits and a–v only (base32hex), 5–1024 characters. */
    static String eventId(Long companyId, ECalendar kind, String sourceId) {
        if (companyId == null || sourceId == null || !sourceId.matches("[0-9]{1,19}")) {
            return null;
        }
        return "erp" + companyId + (kind == ECalendar.INVOICE_DUE ? "inv" : "hol") + sourceId;
    }

    /** All-day event; end.date is exclusive (the day after the last day). null when the payload lacks the dates. */
    ObjectNode eventBody(ECalendar kind, String id, JsonNode data) {
        if (data == null || id == null) {
            return null;
        }
        ObjectNode e = mapper.createObjectNode().put("id", id).put("transparency", "transparent");
        if (kind == ECalendar.INVOICE_DUE) {
            LocalDate due = date(data.path("dueDate"));
            String number = data.path("number").asText("").trim();
            if (due == null || number.isBlank()) {
                return null;
            }
            String customer = data.path("customerName").asText("").trim();
            e.put("summary", "Invoice " + number + " due" + (customer.isBlank() ? "" : " — " + customer));
            String total = data.path("total").isMissingNode() || data.path("total").isNull() ? ""
                    : data.path("total").decimalValue().stripTrailingZeros().toPlainString();
            e.put("description", "Sales invoice " + number + (total.isBlank() ? "" : ", total " + total)
                    + ". Added by ERP.");
            e.putObject("start").put("date", due.toString());
            e.putObject("end").put("date", due.plusDays(1).toString());
            return e;
        }
        LocalDate start = date(data.path("startDate"));
        LocalDate end = date(data.path("endDate"));
        String name = data.path("name").asText("").trim();
        if (start == null || name.isBlank()) {
            return null;
        }
        if (end == null || end.isBefore(start)) {
            end = start;
        }
        e.put("summary", name);
        e.put("description", "Holiday. Added by ERP.");
        e.putObject("start").put("date", start.toString());
        e.putObject("end").put("date", end.plusDays(1).toString());
        return e;
    }

    /** "2026-12-25", or [2026,12,25] when the producer wrote dates as arrays. */
    private static LocalDate date(JsonNode n) {
        try {
            if (n.isTextual()) {
                return LocalDate.parse(n.asText());
            }
            if (n.isArray() && n.size() == 3) {
                return LocalDate.of(n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt());
            }
        } catch (java.time.DateTimeException e) {
            return null;
        }
        return null;
    }

    private static boolean validCalendarId(String id) {
        return id != null && !id.isBlank() && id.trim().length() <= 255 && id.trim().matches("[^\\s/\\\\?#%]+");
    }

    /** One URL path segment ("@" becomes %40, which Google accepts). */
    private static String pathSegment(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private String json(JsonNode n) {
        try {
            return mapper.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unserializable calendar event", e);
        }
    }
}
