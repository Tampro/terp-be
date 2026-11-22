package com.terp.erp.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * One POST to a URL that {@link UrlGuard} has already accepted: 3 s connect, 5 s total, redirects never followed,
 * the response body discarded (only its status is reported, so a test cannot be used to read an internal page).
 * No retry: a webhook POST is not idempotent. Never log the URL: Slack / Telegram URLs carry the secret.
 * Not a Spring bean: services declare one (iam-service subclasses it as a component).
 */
public class OutboundHttp {
    /** statusCode null = no HTTP answer; failure = TIMEOUT or CONNECTION_ERROR then. */
    public record Result(boolean delivered, Integer statusCode, String failure) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Result postJson(URI uri, String json, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("User-Agent", "ERP-Webhook/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(b::header);
        try {
            HttpResponse<Void> r = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
            int s = r.statusCode();
            return new Result(s >= 200 && s < 300, s, null);
        } catch (HttpTimeoutException e) {
            return new Result(false, null, "TIMEOUT");
        } catch (IOException e) {
            return new Result(false, null, "CONNECTION_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, null, "CONNECTION_ERROR");
        }
    }
}
