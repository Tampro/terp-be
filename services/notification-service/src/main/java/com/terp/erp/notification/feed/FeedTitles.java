package com.terp.erp.notification.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.notification.channel.MessageTemplates;
import com.terp.erp.notification.event.EEventRoute;

/**
 * The one-line title of a feed row, from display fields only. A routed event reuses the chat line (an email subject
 * or the webhook name + number / title; never LEAD_EMAIL / DEAL_EMAIL's own subject); the chat event names the
 * sender and never carries the message. TODO(assumption): built-in en/vi lines until BA writes the feed texts.
 */
public final class FeedTitles {
    public static final String CHAT_EVENT = "support.chat.message_sent";
    private static final String PREFIX = "[ERP] ";
    private static final int MAX = 255;

    private FeedTitles() {
    }

    public static String title(String eventType, String language, JsonNode data) {
        String t;
        if (CHAT_EVENT.equals(eventType)) {
            String name = text(data, "senderName");
            boolean vi = MessageTemplates.vietnamese(language);
            t = name.isBlank() ? (vi ? "Tin nhắn mới" : "New message")
                    : (vi ? "Tin nhắn mới từ " : "New message from ") + name;
        } else {
            t = EEventRoute.of(eventType).map(r -> MessageTemplates.chat(r, language, data)).orElseGet(() -> {
                String label = text(data, "number");
                return label.isBlank() ? text(data, "title") : label;
            });
            if (t.startsWith(PREFIX)) {
                t = t.substring(PREFIX.length());
            }
            if (t.isBlank()) {
                t = eventType;
            }
        }
        t = t.replaceAll("[\\r\\n]+", " ").trim();
        return t.length() > MAX ? t.substring(0, MAX) : t;
    }

    private static String text(JsonNode data, String key) {
        return data == null ? "" : data.path(key).asText("").trim();
    }
}
