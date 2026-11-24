package com.terp.erp.iam.ai;

import com.terp.erp.net.PinnedHttp;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * One POST to an AI provider URL already accepted by UrlGuard, through {@link PinnedHttp} (the address connected to
 * is the one checked): 5 s connect, 30 s for the answer, redirects never followed, no retry (a completion costs money
 * and is not idempotent). Unlike the webhook OutboundHttp, the body is read (it is the generated text) but capped,
 * so a misbehaving host cannot fill memory. Never logs anything: the headers carry the API key and the body carries
 * the user's text.
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

    private final PinnedHttp http = new PinnedHttp(Duration.ofSeconds(5), Duration.ofSeconds(30),
            "ERP-AI-Assistant/1.0");

    public Response postJson(URI uri, String json, Map<String, String> headers) {
        HttpPost post = new HttpPost(uri);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
        headers.forEach(post::setHeader);
        PinnedHttp.Exchange x = http.execute(post, MAX_BODY_BYTES);
        if (x.status() == null) {
            throw new TransportException(x.failure());
        }
        String body = x.truncated() ? null
                : x.body() == null ? "" : new String(x.body(), StandardCharsets.UTF_8);
        return new Response(x.status(), body);
    }
}
