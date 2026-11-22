package com.terp.erp.notification.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.net.OutboundHttp;
import com.terp.erp.net.UrlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

/**
 * One line of text to Slack (incoming webhook, only hooks.slack.com) or Telegram (sendMessage, only
 * api.telegram.org), with the same SSRF rules as IAM's test buttons. Never logs the URL: it carries the secret.
 */
@Component
@RequiredArgsConstructor
public class ChatChannel {
    static final String SLACK_HOST = "hooks.slack.com";
    static final String TELEGRAM_HOST = "api.telegram.org";

    private final UrlGuard guard;
    private final OutboundHttp http;
    private final ObjectMapper mapper;

    public SendResult slack(String webhookUrl, String text) {
        URI uri;
        try {
            uri = guard.check(webhookUrl, SLACK_HOST);
        } catch (UrlGuard.RefusedException e) {
            return SendResult.fail("URL_REFUSED");
        }
        return SendResult.ofHttp(http.postJson(uri, json(Map.of("text", text)), Map.of()));
    }

    public SendResult telegram(String botToken, String chatId, String text) {
        if (botToken == null || !botToken.matches("[0-9]{3,20}:[A-Za-z0-9_-]{20,80}") || chatId == null) {
            return SendResult.fail("INVALID_CONFIG");
        }
        URI uri;
        try {
            uri = guard.check("https://" + TELEGRAM_HOST + "/bot" + botToken + "/sendMessage", TELEGRAM_HOST);
        } catch (UrlGuard.RefusedException e) {
            return SendResult.fail("URL_REFUSED");
        }
        return SendResult.ofHttp(http.postJson(uri, json(Map.of("chat_id", chatId, "text", text)), Map.of()));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("not serializable", e);
        }
    }
}
