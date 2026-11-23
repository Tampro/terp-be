package com.terp.erp.iam.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.ESaasError;
import com.terp.erp.iam.settings.SettingsService.ActivePaypal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PayPal REST API (Orders v2), called directly with the JDK HttpClient (no SDK): OAuth client-credentials token,
 * create order, capture, get order. The host comes from the mode only (api-m.sandbox.paypal.com / api-m.paypal.com),
 * so credentials never go anywhere else. 5 s connect, 20 s per answer, redirects never followed, no automatic retry
 * (create and capture carry PayPal-Request-Id, so a retry by the caller is idempotent at PayPal). Never logs the
 * token, the secret, request bodies or payer data — only status codes and PayPal issue names.
 */
@Slf4j
@Component
public class PaypalClient {
    /** Currencies PayPal accepts for payments (developer.paypal.com/reference/currency-codes). VND is not one. */
    public static final Set<String> SUPPORTED = Set.of("AUD", "BRL", "CAD", "CNY", "CZK", "DKK", "EUR", "HKD", "HUF",
            "ILS", "JPY", "MYR", "MXN", "TWD", "NZD", "NOK", "PHP", "PLN", "GBP", "SGD", "SEK", "CHF", "THB", "USD");
    /** PayPal refuses decimals for these. */
    static final Set<String> NO_DECIMALS = Set.of("HUF", "JPY", "TWD");

    /** One HTTP exchange; a seam so tests never reach PayPal. IOException = no answer (timeout, connection). */
    public interface Transport {
        Response send(String method, URI uri, Map<String, String> headers, String body) throws IOException;
    }

    public record Response(int status, String body) {
        @Override
        public String toString() {
            return "Response[status=" + status + "]";
        }
    }

    public record Created(String paypalOrderId, String approveUrl) {
    }

    /** What PayPal says about an order after capture; every field comes from PayPal's answer. */
    public record Captured(String orderStatus, String referenceId, String customId, String captureId,
                           String captureStatus, String currency, String value) {
    }

    /** 422 ORDER_ALREADY_CAPTURED: read the order instead. */
    public static class AlreadyCapturedException extends RuntimeException {
        public AlreadyCapturedException() {
            super("ORDER_ALREADY_CAPTURED", null, false, false);
        }
    }

    private record Token(String value, Instant expiresAt) {
        @Override
        public String toString() {
            return "Token[" + expiresAt + "]";
        }
    }

    private final Transport transport;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    @Autowired
    public PaypalClient(ObjectMapper mapper, Clock clock) {
        this(jdkTransport(), mapper, clock);
    }

    PaypalClient(Transport transport, ObjectMapper mapper, Clock clock) {
        this.transport = transport;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** "12.50" for USD, "490" for JPY; PayPal wants a string with the currency's exact decimals. */
    public static String value(BigDecimal amount, String currency) {
        int scale = NO_DECIMALS.contains(currency.toUpperCase(Locale.ROOT)) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ OAuth

    /** Cached per mode + client id + secret until 60 s before expiry; a 401 drops it. */
    String token(ActivePaypal p) {
        String key = cacheKey(p);
        Token t = tokens.get(key);
        if (t != null && clock.instant().isBefore(t.expiresAt())) {
            return t.value();
        }
        String basic = Base64.getEncoder().encodeToString(
                (p.clientId() + ":" + p.clientSecret()).getBytes(StandardCharsets.UTF_8));
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Basic " + basic);
        h.put("Content-Type", "application/x-www-form-urlencoded");
        h.put("Accept", "application/json");
        Response r = call("POST", p, "/v1/oauth2/token", h, "grant_type=client_credentials");
        if (r.status() == 401 || r.status() == 403) {
            log.warn("PayPal OAuth refused the credentials: HTTP {}", r.status());
            throw ApiException.badRequest(ESaasError.SAAS_044);
        }
        failOnError(r, "oauth");
        JsonNode body = json(r);
        String value = body.path("access_token").asText("");
        if (value.isEmpty()) {
            throw unavailable("oauth: no token in the answer");
        }
        long ttl = Math.max(0, body.path("expires_in").asLong(0) - 60);
        tokens.put(key, new Token(value, clock.instant().plusSeconds(ttl)));
        return value;
    }

    // ------------------------------------------------------------------ Orders v2

    /** intent CAPTURE, one purchase unit; requestId makes a repeated create return the same PayPal order. */
    public Created createOrder(ActivePaypal p, String referenceId, String customId, String description,
                               String currency, String value, String returnUrl, String cancelUrl, String requestId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("intent", "CAPTURE");
        ArrayNode units = root.putArray("purchase_units");
        ObjectNode unit = units.addObject();
        unit.put("reference_id", referenceId);
        unit.put("custom_id", customId);
        unit.put("description", description.length() > 127 ? description.substring(0, 127) : description);
        ObjectNode amount = unit.putObject("amount");
        amount.put("currency_code", currency);
        amount.put("value", value);
        ObjectNode ctx = root.putObject("payment_source").putObject("paypal").putObject("experience_context");
        ctx.put("return_url", returnUrl);
        ctx.put("cancel_url", cancelUrl);
        ctx.put("user_action", "PAY_NOW");
        ctx.put("shipping_preference", "NO_SHIPPING");
        Response r = authed(p, "POST", "/v2/checkout/orders", root.toString(), requestId);
        failOnError(r, "create order");
        JsonNode body = json(r);
        String id = body.path("id").asText("");
        String approve = null;
        for (JsonNode link : body.path("links")) {
            String rel = link.path("rel").asText();
            if ("payer-action".equals(rel) || ("approve".equals(rel) && approve == null)) {
                approve = link.path("href").asText(null);
            }
        }
        if (id.isEmpty() || approve == null || !approve.startsWith("https://")) {
            throw unavailable("create order: no id or approval link in the answer");
        }
        return new Created(id, approve);
    }

    /** Captures an approved order. 422 ORDER_ALREADY_CAPTURED → {@link AlreadyCapturedException}. */
    public Captured capture(ActivePaypal p, String paypalOrderId, String requestId) {
        Response r = authed(p, "POST", "/v2/checkout/orders/" + enc(paypalOrderId) + "/capture", "{}", requestId);
        if (r.status() == 422) {
            String issue = issue(r);
            if ("ORDER_ALREADY_CAPTURED".equals(issue)) {
                throw new AlreadyCapturedException();
            }
            if ("ORDER_NOT_APPROVED".equals(issue) || "PAYER_ACTION_REQUIRED".equals(issue)) {
                throw ApiException.badRequest(ESaasError.SAAS_046);
            }
        }
        failOnError(r, "capture");
        return captured(json(r));
    }

    public Captured getOrder(ActivePaypal p, String paypalOrderId) {
        Response r = authed(p, "GET", "/v2/checkout/orders/" + enc(paypalOrderId), null, null);
        failOnError(r, "get order");
        return captured(json(r));
    }

    /** The capture answer and GET order put custom_id in different places: read both. */
    static Captured captured(JsonNode order) {
        JsonNode unit = order.path("purchase_units").path(0);
        JsonNode capture = unit.path("payments").path("captures").path(0);
        String customId = unit.path("custom_id").asText(null);
        if (customId == null) {
            customId = capture.path("custom_id").asText(null);
        }
        return new Captured(order.path("status").asText(""), unit.path("reference_id").asText(null), customId,
                capture.path("id").asText(null), capture.path("status").asText(null),
                capture.path("amount").path("currency_code").asText(null),
                capture.path("amount").path("value").asText(null));
    }

    // ------------------------------------------------------------------ plumbing

    private Response authed(ActivePaypal p, String method, String path, String body, String requestId) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Bearer " + token(p));
        h.put("Accept", "application/json");
        if (body != null) {
            h.put("Content-Type", "application/json");
        }
        if (requestId != null) {
            h.put("PayPal-Request-Id", requestId);
        }
        Response r = call(method, p, path, h, body);
        if (r.status() == 401) {
            // A token PayPal no longer accepts: forget it so the next call fetches a fresh one.
            tokens.remove(cacheKey(p));
        }
        return r;
    }

    private Response call(String method, ActivePaypal p, String path, Map<String, String> headers, String body) {
        try {
            return transport.send(method, URI.create(p.mode().baseUrl() + path), headers, body);
        } catch (IOException e) {
            log.warn("PayPal unreachable: {}", e.getClass().getSimpleName());
            throw unavailable("unreachable");
        }
    }

    private void failOnError(Response r, String step) {
        if (r.status() >= 200 && r.status() < 300) {
            return;
        }
        if (r.status() >= 500 || r.status() == 429 || r.status() == 401) {
            throw unavailable(step + ": HTTP " + r.status());
        }
        String issue = issue(r);
        log.warn("PayPal refused {}: HTTP {} {}", step, r.status(), issue);
        throw ApiException.badRequest(ESaasError.SAAS_047, issue == null ? "HTTP " + r.status() : issue);
    }

    private ApiException unavailable(String why) {
        log.warn("PayPal unavailable: {}", why);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ESaasError.SAAS_045);
    }

    /** PayPal's machine-readable issue name (details[0].issue, else name); never the free-text description. */
    private String issue(Response r) {
        try {
            JsonNode b = mapper.readTree(r.body() == null ? "" : r.body());
            String issue = b.path("details").path(0).path("issue").asText("");
            if (issue.isEmpty()) {
                issue = b.path("name").asText(b.path("error").asText(""));
            }
            return issue.isEmpty() || !issue.matches("[A-Za-z0-9_]{1,80}") ? null : issue;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode json(Response r) {
        try {
            return mapper.readTree(r.body() == null ? "" : r.body());
        } catch (Exception e) {
            throw unavailable("unreadable answer");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String cacheKey(ActivePaypal p) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(
                    (p.mode() + "|" + p.clientId() + "|" + p.clientSecret()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Transport jdkTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (method, uri, headers, body) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "ERP-Billing/1.0")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(b::header);
            try {
                HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return new Response(r.statusCode(), r.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        };
    }
}
