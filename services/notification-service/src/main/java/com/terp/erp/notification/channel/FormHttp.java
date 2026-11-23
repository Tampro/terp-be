package com.terp.erp.notification.channel;

import com.terp.erp.net.OutboundHttp;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * The form-encoded twin of {@link OutboundHttp#postJson} (Twilio takes application/x-www-form-urlencoded): same 3 s
 * connect / 5 s total, redirects never followed, body discarded. To a URL {@code UrlGuard} accepted; never log it,
 * its headers or its body (they carry the auth token, a phone number and the message).
 */
@Component
public class FormHttp {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public OutboundHttp.Result postForm(URI uri, String formBody, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "ERP-Notification/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        headers.forEach(b::header);
        try {
            HttpResponse<Void> r = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
            int s = r.statusCode();
            return new OutboundHttp.Result(s >= 200 && s < 300, s, null);
        } catch (HttpTimeoutException e) {
            return new OutboundHttp.Result(false, null, "TIMEOUT");
        } catch (IOException e) {
            return new OutboundHttp.Result(false, null, "CONNECTION_ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OutboundHttp.Result(false, null, "CONNECTION_ERROR");
        }
    }
}
