package com.terp.erp.notification.channel;

import com.terp.erp.net.UrlGuard;
import com.terp.erp.notification.client.IamInternalClient.Twilio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * One SMS through Twilio's Messages API (POST /2010-04-01/Accounts/{sid}/Messages.json, form To / From / Body, basic
 * auth sid:token); the host is pinned to api.twilio.com by {@link UrlGuard}.
 * Never logs the token, the phone numbers or the text. Codes: INVALID_CONFIG (sid / from malformed), NO_RECIPIENT,
 * URL_REFUSED, else {@link SendResult#ofHttp} (HTTP_401 bad credentials, HTTP_400 refused number…).
 */
@Component
@RequiredArgsConstructor
public class SmsChannel {
    static final String TWILIO_HOST = "api.twilio.com";
    /** Twilio's limit is 1600 characters per message (several segments). */
    static final int MAX_BODY = 1600;
    private static final String E164 = "\\+[1-9][0-9]{6,14}";

    private final UrlGuard guard;
    private final FormHttp http;

    public SendResult send(Twilio cfg, String to, String text) {
        if (cfg == null || cfg.accountSid() == null || !cfg.accountSid().matches("AC[0-9a-fA-F]{32}")
                || cfg.authToken() == null || cfg.authToken().isBlank()
                || cfg.fromNumber() == null || !cfg.fromNumber().matches(E164)) {
            return SendResult.fail("INVALID_CONFIG");
        }
        if (to == null || !to.matches(E164)) {
            return SendResult.fail("NO_RECIPIENT");
        }
        URI uri;
        try {
            uri = guard.check("https://" + TWILIO_HOST + "/2010-04-01/Accounts/" + cfg.accountSid()
                    + "/Messages.json", TWILIO_HOST);
        } catch (UrlGuard.RefusedException e) {
            return SendResult.fail("URL_REFUSED");
        }
        String body = text == null ? "" : text;
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
        }
        String form = "To=" + enc(to) + "&From=" + enc(cfg.fromNumber()) + "&Body=" + enc(body);
        String basic = Base64.getEncoder().encodeToString((cfg.accountSid() + ":" + cfg.authToken())
                .getBytes(StandardCharsets.UTF_8));
        return SendResult.ofHttp(http.postForm(uri, form, Map.of("Authorization", "Basic " + basic)));
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
