package com.terp.erp.notification.channel;

import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.PinnedHttp;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * The form-encoded twin of {@link OutboundHttp#postJson} (Twilio takes application/x-www-form-urlencoded): same
 * {@link PinnedHttp} rules, 3 s connect / 5 s for the answer, redirects never followed, body discarded. To a URL
 * {@code UrlGuard} accepted; never log it, its headers or its body (they carry the auth token, a phone number and
 * the message).
 */
@Component
public class FormHttp {
    private final PinnedHttp http = new PinnedHttp(Duration.ofSeconds(3), Duration.ofSeconds(5),
            "ERP-Notification/1.0");

    public OutboundHttp.Result postForm(URI uri, String formBody, Map<String, String> headers) {
        HttpPost post = new HttpPost(uri);
        post.setEntity(new StringEntity(formBody, ContentType.APPLICATION_FORM_URLENCODED));
        headers.forEach(post::setHeader);
        return OutboundHttp.of(http.execute(post, 0));
    }
}
