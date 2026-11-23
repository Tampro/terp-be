package com.terp.erp.iam.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.saas.ESaasError;
import com.terp.erp.iam.settings.SettingsDtos.EPaypalMode;
import com.terp.erp.iam.settings.SettingsService.ActivePaypal;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PayPal is never called: a fake transport records every request and answers from a script. */
class PaypalClientTest {
    static final ActivePaypal SANDBOX = new ActivePaypal(EPaypalMode.SANDBOX, "fake-client", "fake-secret");
    static final String TOKEN = "{\"access_token\":\"tok-1\",\"expires_in\":3600}";

    record Req(String method, URI uri, Map<String, String> headers, String body) {
    }

    static class Fake implements PaypalClient.Transport {
        final List<Req> requests = new ArrayList<>();
        Function<Req, PaypalClient.Response> answer;

        @Override
        public PaypalClient.Response send(String method, URI uri, Map<String, String> headers, String body) {
            Req r = new Req(method, uri, Map.copyOf(headers), body);
            requests.add(r);
            return answer.apply(r);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Fake fake = new Fake();
    private Instant now = Instant.parse("2026-11-23T10:00:00Z");
    private final PaypalClient client = new PaypalClient(fake, mapper, new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    @Test
    void tokenRequestIsClientCredentialsWithBasicAuthAndIsCachedUntilExpiryMinus60s() {
        fake.answer = r -> new PaypalClient.Response(200, TOKEN);
        assertThat(client.token(SANDBOX)).isEqualTo("tok-1");
        Req r = fake.requests.get(0);
        assertThat(r.method()).isEqualTo("POST");
        assertThat(r.uri().toString()).isEqualTo("https://api-m.sandbox.paypal.com/v1/oauth2/token");
        assertThat(r.body()).isEqualTo("grant_type=client_credentials");
        assertThat(r.headers().get("Authorization")).isEqualTo("Basic "
                + Base64.getEncoder().encodeToString("fake-client:fake-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(r.headers().get("Content-Type")).isEqualTo("application/x-www-form-urlencoded");

        now = now.plusSeconds(3539);
        client.token(SANDBOX);
        assertThat(fake.requests).hasSize(1);
        now = now.plusSeconds(2);
        client.token(SANDBOX);
        assertThat(fake.requests).hasSize(2);
    }

    @Test
    void liveModeUsesTheLiveHostOnly() {
        fake.answer = r -> new PaypalClient.Response(200, TOKEN);
        client.token(new ActivePaypal(EPaypalMode.LIVE, "c", "s"));
        assertThat(fake.requests.get(0).uri().getHost()).isEqualTo("api-m.paypal.com");
    }

    @Test
    void refusedCredentialsAreSaas044AndOutagesSaas045() {
        fake.answer = r -> new PaypalClient.Response(401, "{\"error\":\"invalid_client\"}");
        assertError(() -> client.token(SANDBOX), ESaasError.SAAS_044, HttpStatus.BAD_REQUEST);
        fake.answer = r -> new PaypalClient.Response(503, "");
        assertError(() -> client.token(SANDBOX), ESaasError.SAAS_045, HttpStatus.SERVICE_UNAVAILABLE);
        PaypalClient down = new PaypalClient((m, u, h, b) -> {
            throw new IOException("timeout");
        }, mapper, Clock.systemUTC());
        assertError(() -> down.token(SANDBOX), ESaasError.SAAS_045, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void createOrderBodyAndApproveLink() throws Exception {
        fake.answer = r -> r.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(200, "{\"id\":\"PP-1\",\"status\":\"PAYER_ACTION_REQUIRED\",\"links\":["
                + "{\"rel\":\"self\",\"href\":\"https://api-m.sandbox.paypal.com/v2/checkout/orders/PP-1\"},"
                + "{\"rel\":\"payer-action\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PP-1\"}]}");
        var created = client.createOrder(SANDBOX, "42", "10:42", "Pro (monthly) ORD1", "USD",
                PaypalClient.value(new BigDecimal("19.9"), "USD"), "https://app/return", "https://app/cancel",
                "erp-order-42-create-19.90");
        assertThat(created.paypalOrderId()).isEqualTo("PP-1");
        assertThat(created.approveUrl()).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=PP-1");

        Req r = fake.requests.get(1);
        assertThat(r.uri().toString()).isEqualTo("https://api-m.sandbox.paypal.com/v2/checkout/orders");
        assertThat(r.headers().get("Authorization")).isEqualTo("Bearer tok-1");
        assertThat(r.headers().get("PayPal-Request-Id")).isEqualTo("erp-order-42-create-19.90");
        JsonNode b = mapper.readTree(r.body());
        assertThat(b.path("intent").asText()).isEqualTo("CAPTURE");
        JsonNode unit = b.path("purchase_units").path(0);
        assertThat(b.path("purchase_units")).hasSize(1);
        assertThat(unit.path("reference_id").asText()).isEqualTo("42");
        assertThat(unit.path("custom_id").asText()).isEqualTo("10:42");
        assertThat(unit.path("amount").path("currency_code").asText()).isEqualTo("USD");
        assertThat(unit.path("amount").path("value").asText()).isEqualTo("19.90");
        JsonNode ctx = b.path("payment_source").path("paypal").path("experience_context");
        assertThat(ctx.path("return_url").asText()).isEqualTo("https://app/return");
        assertThat(ctx.path("cancel_url").asText()).isEqualTo("https://app/cancel");
    }

    @Test
    void amountsHaveTheCurrencysDecimals() {
        assertThat(PaypalClient.value(new BigDecimal("10"), "USD")).isEqualTo("10.00");
        assertThat(PaypalClient.value(new BigDecimal("10.005"), "EUR")).isEqualTo("10.01");
        assertThat(PaypalClient.value(new BigDecimal("1000.4"), "JPY")).isEqualTo("1000");
        assertThat(PaypalClient.SUPPORTED).doesNotContain("VND");
    }

    @Test
    void captureParsesPaypalsAnswerAndMapsAlreadyCapturedAndNotApproved() {
        fake.answer = r -> r.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(201, captureJson("COMPLETED", "42", "10:42", "USD", "19.90"));
        var c = client.capture(SANDBOX, "PP-1", "erp-order-42-capture");
        assertThat(c).isEqualTo(new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "COMPLETED", "USD",
                "19.90"));
        Req r = fake.requests.get(1);
        assertThat(r.uri().toString()).isEqualTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/PP-1/capture");
        assertThat(r.headers().get("PayPal-Request-Id")).isEqualTo("erp-order-42-capture");

        fake.answer = r2 -> r2.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(422, "{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":"
                + "\"ORDER_ALREADY_CAPTURED\"}]}");
        assertThatThrownBy(() -> client.capture(SANDBOX, "PP-1", "x"))
                .isInstanceOf(PaypalClient.AlreadyCapturedException.class);

        fake.answer = r2 -> r2.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(422, "{\"details\":[{\"issue\":\"ORDER_NOT_APPROVED\"}]}");
        assertError(() -> client.capture(SANDBOX, "PP-1", "x"), ESaasError.SAAS_046, HttpStatus.BAD_REQUEST);

        fake.answer = r2 -> r2.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(400, "{\"details\":[{\"issue\":\"INVALID_PARAMETER_VALUE\","
                + "\"description\":\"payer@example.com\"}]}");
        assertThatThrownBy(() -> client.capture(SANDBOX, "PP-1", "x"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ESaasError.SAAS_047);
                    assertThat(e.getArgs()).containsExactly("INVALID_PARAMETER_VALUE");
                });
    }

    @Test
    void getOrderReadsCustomIdOfThePurchaseUnit() {
        fake.answer = r -> r.uri().getPath().endsWith("/token") ? new PaypalClient.Response(200, TOKEN)
                : new PaypalClient.Response(200, "{\"status\":\"COMPLETED\",\"purchase_units\":[{\"reference_id\":"
                + "\"42\",\"custom_id\":\"10:42\",\"payments\":{\"captures\":[{\"id\":\"CAP-1\",\"status\":"
                + "\"COMPLETED\",\"amount\":{\"currency_code\":\"USD\",\"value\":\"19.90\"}}]}}]}");
        var c = client.getOrder(SANDBOX, "PP-1");
        assertThat(c.customId()).isEqualTo("10:42");
        assertThat(fake.requests.get(1).method()).isEqualTo("GET");
    }

    @Test
    void secretsNeverAppearInToStrings() {
        assertThat(SANDBOX.toString()).doesNotContain("fake-secret").doesNotContain("fake-client");
        assertThat(new PaypalClient.Response(200, TOKEN).toString()).doesNotContain("tok-1");
    }

    static String captureJson(String status, String ref, String custom, String cur, String value) {
        return "{\"id\":\"PP-1\",\"status\":\"" + status + "\",\"payer\":{\"email_address\":\"payer@example.com\"},"
                + "\"purchase_units\":[{\"reference_id\":\"" + ref + "\",\"payments\":{\"captures\":[{\"id\":"
                + "\"CAP-1\",\"status\":\"" + status + "\",\"custom_id\":\"" + custom + "\",\"amount\":{"
                + "\"currency_code\":\"" + cur + "\",\"value\":\"" + value + "\"}}]}}]}";
    }

    private static void assertError(Runnable call, ESaasError code, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(status);
        });
    }
}
