package com.terp.erp.notification.channel;

import com.terp.erp.net.PinnedHttp;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Calls to Google's token and Calendar endpoints, to a URL {@code UrlGuard} accepted, through {@link PinnedHttp}:
 * 3 s connect / 5 s for the answer, redirects never followed. Only the token answer is read (bounded to
 * {@link #MAX_BODY}: it holds the access token); the Calendar answers are discarded. Never log a request, a header
 * or a body (key, assertion, token).
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

    private final PinnedHttp http = new PinnedHttp(Duration.ofSeconds(3), Duration.ofSeconds(5),
            "ERP-Notification/1.0");

    /** POST application/x-www-form-urlencoded; the answer's body is returned (the token endpoint). */
    public Response postFormForBody(URI uri, String formBody) {
        var r = ClassicRequestBuilder.post(uri)
                .setEntity(new StringEntity(formBody, ContentType.APPLICATION_FORM_URLENCODED)).build();
        PinnedHttp.Exchange x = http.execute(r, MAX_BODY);
        if (x.status() == null) {
            return new Response(null, null, x.failure());
        }
        return new Response(x.status(), x.body() == null ? "" : new String(x.body(), StandardCharsets.UTF_8), null);
    }

    /** method POST / PUT with a JSON body, or DELETE (json null); the body of the answer is discarded. */
    public Response send(String method, URI uri, String json, Map<String, String> headers) {
        ClassicRequestBuilder b = ClassicRequestBuilder.create(method).setUri(uri);
        if (json != null) {
            b.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        }
        headers.forEach(b::setHeader);
        PinnedHttp.Exchange x = http.execute(b.build(), 0);
        return new Response(x.status(), null, x.failure());
    }
}
