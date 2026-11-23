package com.terp.erp.notification.channel;

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
 * Calls to Google's token and Calendar endpoints, to a URL {@code UrlGuard} accepted: 3 s connect / 5 s total,
 * redirects never followed. Only the token answer is read (bounded to {@link #MAX_BODY}: it holds the access
 * token); the Calendar answers are discarded. Never log a request, a header or a body (key, assertion, token).
 */
@Component
public class GoogleHttp {
    static final int MAX_BODY = 16 * 1024;

    /** status null = no HTTP answer; failure = TIMEOUT or CONNECTION_ERROR then. body only when asked for. */
    public record Response(Integer status, String body, String failure) {
        public boolean ok() {
            return status != null && status >= 200 && status < 300;
        }

        @Override
        public String toString() {
            return "Response[status=" + status + ", failure=" + failure + "]";
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** POST application/x-www-form-urlencoded; the answer's body is returned (the token endpoint). */
    public Response postFormForBody(URI uri, String formBody) {
        HttpRequest r = base(uri, Map.of())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody)).build();
        try {
            HttpResponse<InputStream> res = client.send(r, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                byte[] b = in.readNBytes(MAX_BODY);
                return new Response(res.statusCode(), new String(b, StandardCharsets.UTF_8), null);
            }
        } catch (HttpTimeoutException e) {
            return new Response(null, null, "TIMEOUT");
        } catch (IOException e) {
            return new Response(null, null, "CONNECTION_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(null, null, "CONNECTION_ERROR");
        }
    }

    /** method POST / PUT with a JSON body, or DELETE (json null); the body of the answer is discarded. */
    public Response send(String method, URI uri, String json, Map<String, String> headers) {
        HttpRequest.Builder b = base(uri, headers);
        if (json == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        try {
            HttpResponse<Void> res = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
            return new Response(res.statusCode(), null, null);
        } catch (HttpTimeoutException e) {
            return new Response(null, null, "TIMEOUT");
        } catch (IOException e) {
            return new Response(null, null, "CONNECTION_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(null, null, "CONNECTION_ERROR");
        }
    }

    private static HttpRequest.Builder base(URI uri, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("User-Agent", "ERP-Notification/1.0");
        headers.forEach(b::header);
        return b;
    }
}
