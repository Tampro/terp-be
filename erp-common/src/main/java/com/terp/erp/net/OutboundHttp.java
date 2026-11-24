package com.terp.erp.net;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * One POST to a URL that {@link UrlGuard} has already accepted, through {@link PinnedHttp} (the address connected to
 * is the one checked): 3 s connect, 5 s for the answer, redirects never followed, the response body discarded (only
 * its status is reported, so a test cannot be used to read an internal page). No retry: a webhook POST is not
 * idempotent. Never log the URL: Slack / Telegram URLs carry the secret.
 * Not a Spring bean: services declare one (iam-service subclasses it as a component).
 */
public class OutboundHttp {
    /** statusCode null = no HTTP answer; failure = TIMEOUT or CONNECTION_ERROR then. */
    public record Result(boolean delivered, Integer statusCode, String failure) {
    }

    private final PinnedHttp http = new PinnedHttp(Duration.ofSeconds(3), Duration.ofSeconds(5), "ERP-Webhook/1.0");

    public Result postJson(URI uri, String json, Map<String, String> headers) {
        HttpPost post = new HttpPost(uri);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        headers.forEach(post::setHeader);
        return of(http.execute(post, 0));
    }

    /** The webhook-style result of an exchange: delivered = 2xx. */
    public static Result of(PinnedHttp.Exchange x) {
        if (x.status() == null) {
            return new Result(false, null, x.failure());
        }
        int s = x.status();
        return new Result(s >= 200 && s < 300, s, null);
    }
}
