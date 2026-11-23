package com.terp.erp.iam.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * One POST to an AI provider URL already accepted by UrlGuard: 5 s connect, 30 s for the answer, redirects never
 * followed, no retry (a completion costs money and is not idempotent). Unlike the webhook OutboundHttp, the body is
 * read (it is the generated text) but capped, so a misbehaving host cannot fill memory. Never logs anything: the
 * headers carry the API key and the body carries the user's text.
 */
@Component
public class AiHttp {
    static final int MAX_BODY_BYTES = 256 * 1024;

    /** body null when the answer was larger than {@link #MAX_BODY_BYTES}. */
    public record Response(int status, String body) {
        @Override
        public String toString() {
            return "Response[status=" + status + "]";
        }
    }

    /** No HTTP answer: reason TIMEOUT or CONNECTION_ERROR. */
    public static class TransportException extends RuntimeException {
        private final String reason;

        public TransportException(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Response postJson(URI uri, String json, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("User-Agent", "ERP-AI-Assistant/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        headers.forEach(b::header);
        try {
            HttpResponse<InputStream> r = client.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
                String body = bytes.length > MAX_BODY_BYTES ? null : new String(bytes, StandardCharsets.UTF_8);
                return new Response(r.statusCode(), body);
            }
        } catch (HttpTimeoutException e) {
            throw new TransportException("TIMEOUT");
        } catch (IOException e) {
            throw new TransportException("CONNECTION_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("CONNECTION_ERROR");
        }
    }
}
