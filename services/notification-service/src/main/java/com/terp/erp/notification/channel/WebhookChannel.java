package com.terp.erp.notification.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.event.EEventRoute;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A company webhook delivery (same contract as IAM's test delivery): POST JSON
 * {event, eventName, module, eventId, companyId, occurredAt, data} with X-ERP-Event, and with a signing secret
 * X-ERP-Timestamp (epoch seconds) + X-ERP-Signature: sha256=hex(HMAC-SHA256(secret, timestamp + "." + body)).
 * UrlGuard before the call, 3 s connect / 5 s total, redirects never followed, the answer body never read.
 */
@Component
@RequiredArgsConstructor
public class WebhookChannel {
    private final UrlGuard guard;
    private final OutboundHttp http;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SendResult send(String url, String secret, EEventRoute route, EventEnvelope e) {
        URI uri;
        try {
            uri = guard.check(url, null);
        } catch (UrlGuard.RefusedException ex) {
            return SendResult.fail("URL_REFUSED");
        }
        String json = body(route, e);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-ERP-Event", route.webhookCode);
        if (secret != null && !secret.isBlank()) {
            String ts = String.valueOf(Instant.now(clock).getEpochSecond());
            headers.put("X-ERP-Timestamp", ts);
            headers.put("X-ERP-Signature", "sha256=" + sign(secret, ts + "." + json));
        }
        return SendResult.ofHttp(http.postJson(uri, json, headers));
    }

    String body(EEventRoute route, EventEnvelope e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", route.webhookCode);
        body.put("eventName", route.webhookName);
        body.put("module", route.webhookModule);
        body.put("eventId", e.eventId());
        body.put("companyId", e.companyId() == null ? null : String.valueOf(e.companyId()));
        body.put("occurredAt", e.occurredAt() == null ? null : e.occurredAt().toString());
        body.put("data", e.data());
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("not serializable", ex);
        }
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }
}
