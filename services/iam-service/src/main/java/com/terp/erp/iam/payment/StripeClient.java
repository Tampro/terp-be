package com.terp.erp.iam.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.ESaasError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Stripe REST API, called directly (no SDK, PO 2026-11-21): only Checkout Sessions are needed. The base URL is
 * configurable so tests can point at a stub. The secret key is sent as the Bearer token and never logged.
 */
@Slf4j
@Component
public class StripeClient {
    /** Currencies Stripe counts in whole units (no cents): VND, JPY, KRW… */
    static final Set<String> ZERO_DECIMAL = Set.of("bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg",
            "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

    public record Session(String id, String url) {
    }

    private final RestClient http;
    private final ObjectMapper mapper;

    public StripeClient(@Value("${erp.stripe.api-url:https://api.stripe.com}") String apiUrl, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(20)));
        this.http = RestClient.builder().baseUrl(Nulls.nonNull(apiUrl)).requestFactory(rf).build();
        this.mapper = mapper;
    }

    /** Amount in Stripe's smallest unit: whole units for zero-decimal currencies, else cents. */
    public static long minorUnits(BigDecimal amount, String currency) {
        BigDecimal a = ZERO_DECIMAL.contains(currency.toLowerCase(Locale.ROOT)) ? amount : amount.movePointRight(2);
        return a.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * A one-time Checkout Session for an order. Not retried: a second call would create a second session (the
     * idempotency key makes a retried request return the same one).
     */
    public Session createCheckout(String secretKey, String orderId, String orderNumber, String companyId,
                                  String productName, long amount, String currency, String successUrl,
                                  String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", orderId);
        form.add("metadata[order_id]", orderId);
        form.add("metadata[order_number]", orderNumber);
        form.add("metadata[company_id]", companyId);
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency.toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amount));
        form.add("line_items[0][price_data][product_data][name]", productName);
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        try {
            JsonNode body = http.post().uri("/v1/checkout/sessions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .header("Idempotency-Key", "erp-order-" + orderId + "-" + amount)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            if (body == null || body.path("id").asText("").isEmpty() || body.path("url").asText("").isEmpty()) {
                throw ApiException.badRequest(ESaasError.SAAS_038, "no session in the answer");
            }
            return new Session(body.path("id").asText(), body.path("url").asText());
        } catch (HttpStatusCodeException e) {
            String reason = reason(e);
            log.warn("Stripe refused the checkout session: {} ({})", e.getStatusCode().value(), reason);
            throw ApiException.badRequest(ESaasError.SAAS_038, reason);
        } catch (RestClientException e) {
            log.warn("Stripe unreachable: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest(ESaasError.SAAS_038, "Stripe is unreachable");
        }
    }

    private String reason(HttpStatusCodeException e) {
        try {
            String m = mapper.readTree(e.getResponseBodyAsString()).path("error").path("message").asText("");
            return m.isEmpty() ? "HTTP " + e.getStatusCode().value() : m;
        } catch (Exception ignored) {
            return "HTTP " + e.getStatusCode().value();
        }
    }
}
