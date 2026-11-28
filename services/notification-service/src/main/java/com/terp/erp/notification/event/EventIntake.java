package com.terp.erp.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.NotificationConfig;
import com.terp.erp.notification.client.IamInternalClient.PlatformConfig;
import com.terp.erp.notification.delivery.Delivery;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.delivery.DeliveryRepository;
import com.terp.erp.notification.delivery.Targets;
import com.terp.erp.notification.feed.FeedWriter;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns one broker event into its delivery rows, once: the processed_event row (key = eventId) and the deliveries
 * are written in one transaction, so a redelivered message creates nothing. Rows that cannot be sent at all are
 * SKIPPED right away with a code (email switch off, no recipient, no SMTP, chat channel switched off for the
 * event: CHANNEL_EVENT_DISABLED); a channel that is off for the company gets no row, SMS none without a phone.
 */
@Service
@RequiredArgsConstructor
public class EventIntake {
    /** The consumer sends right after commit; the retry job leaves new rows alone for this long. */
    static final Duration FIRST_SEND_GRACE = Duration.ofMinutes(1);
    public static final String PAYLOAD_RECIPIENT = "payload";
    public static final String USER_RECIPIENT = "user:";
    public static final String ORDER_RECIPIENT = "order:";
    public static final String PLATFORM_RECIPIENT = "platform:";
    public static final String CHANNEL_EVENT_DISABLED = "CHANNEL_EVENT_DISABLED";

    private final ProcessedEventRepository processed;
    private final DeliveryRepository deliveries;
    private final IamInternalClient iam;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final FeedWriter feed;

    /** @return ids of the PENDING deliveries to send now (empty for a duplicate or an unrouted event). */
    @Transactional
    public List<Long> accept(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.eventType() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed event");
        }
        if (processed.existsById(e.eventId())) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setEnvelope(json(e));
        pe.setReceivedAt(now);
        if (e.companyId() == null) {
            // A platform event: the super admin's webhooks and platform team addresses only, never a company's.
            Optional<EPlatformRoute> platform = EPlatformRoute.of(e.eventType());
            if (platform.isEmpty()) {
                processed.saveAndFlush(pe);
                return List.of();
            }
            PlatformConfig pcfg = iam.platformConfig(platform.get().emailCode, platform.get().webhookCode);
            List<Delivery> rows = planPlatform(e, platform.get(), pcfg, now);
            processed.saveAndFlush(pe);
            deliveries.saveAll(rows);
            return rows.stream().filter(d -> d.getStatus() == EStatus.PENDING).map(Delivery::getId).toList();
        }
        Optional<EEventRoute> route = EEventRoute.of(e.eventType());
        if (route.isEmpty()) {
            // Unrouted (the messenger's chat event…): still an in-app notification for its recipients.
            processed.saveAndFlush(pe);
            feed.write(e, null);
            return List.of();
        }
        NotificationConfig cfg = iam.config(e.companyId(), e.eventType(), route.get().emailCode(e.data()),
                route.get().webhookCode);
        List<Delivery> rows = plan(e, route.get(), cfg, now);
        processed.saveAndFlush(pe);
        feed.write(e, cfg.defaultLanguage());
        deliveries.saveAll(rows);
        return rows.stream().filter(d -> d.getStatus() == EStatus.PENDING).map(Delivery::getId).toList();
    }

    List<Delivery> plan(EventEnvelope e, EEventRoute route, NotificationConfig cfg, Instant now) {
        List<Delivery> rows = new ArrayList<>();
        if (route.emailCode(e.data()) != null) {
            List<String> refs = route.customerOrder() ? orderRecipient(e.data()) : recipients(e.data());
            if (!cfg.emailEnabled()) {
                rows.add(row(e, EChannel.EMAIL, null, EStatus.SKIPPED, "EMAIL_DISABLED", now));
            } else if (refs.isEmpty()) {
                rows.add(row(e, EChannel.EMAIL, null, EStatus.SKIPPED, "NO_RECIPIENT", now));
            } else {
                for (String ref : refs) {
                    Delivery d = row(e, EChannel.EMAIL,
                            ref.equals(PAYLOAD_RECIPIENT) ? Targets.maskEmail(e.data().path("recipientEmail").asText())
                                    : null,
                            cfg.smtp() == null ? EStatus.SKIPPED : EStatus.PENDING,
                            cfg.smtp() == null ? "NO_SMTP" : null, now);
                    d.setRecipientRef(ref);
                    rows.add(d);
                }
            }
        }
        if (!route.calendarOnly() && !route.customerOrder()) {
            if (cfg.slack() != null) {
                rows.add(chatRow(e, EChannel.SLACK, "hooks.slack.com", cfg.slackOn(), now));
            }
            if (cfg.telegram() != null) {
                rows.add(chatRow(e, EChannel.TELEGRAM, "api.telegram.org", cfg.telegramOn(), now));
            }
            String phone = phone(e.data());
            if (cfg.twilio() != null && phone != null) {
                rows.add(chatRow(e, EChannel.SMS, Targets.maskPhone(phone), cfg.smsOn(), now));
            }
        }
        // Google Calendar: only when the document asked for it (its flag) and the integration is on; else no row.
        // TODO(assumption): an update / delete with the flag off leaves an entry made earlier in the calendar.
        if (route.calendar != null && cfg.googleCalendar() != null && e.data() != null
                && e.data().path(route.calendar.flag).asBoolean(false)) {
            rows.add(row(e, EChannel.GOOGLE_CALENDAR, Targets.maskCalendarId(cfg.googleCalendar().calendarId()),
                    EStatus.PENDING, null, now));
        }
        if (route.webhookCode != null) {
            for (IamInternalClient.Webhook w : cfg.webhooks()) {
                Delivery d = row(e, EChannel.WEBHOOK, host(w.url()), EStatus.PENDING, null, now);
                d.setWebhookId(Long.valueOf(w.id()));
                rows.add(d);
            }
        }
        return rows;
    }

    /**
     * One EMAIL row per platform team address (recipientRef = platform:&lt;hash&gt;, never the address itself) and one
     * WEBHOOK row per active platform webhook. companyId stays null on every row.
     */
    List<Delivery> planPlatform(EventEnvelope e, EPlatformRoute route, PlatformConfig cfg, Instant now) {
        List<Delivery> rows = new ArrayList<>();
        if (route.emailCode != null) {
            if (!cfg.emailEnabled()) {
                rows.add(row(e, EChannel.EMAIL, null, EStatus.SKIPPED, "EMAIL_DISABLED", now));
            } else if (cfg.recipients().isEmpty()) {
                rows.add(row(e, EChannel.EMAIL, null, EStatus.SKIPPED, "NO_RECIPIENT", now));
            } else {
                for (String address : cfg.recipients().stream().distinct().toList()) {
                    Delivery d = row(e, EChannel.EMAIL, Targets.maskEmail(address),
                            cfg.smtp() == null ? EStatus.SKIPPED : EStatus.PENDING,
                            cfg.smtp() == null ? "NO_SMTP" : null, now);
                    d.setRecipientRef(platformRef(address));
                    rows.add(d);
                }
            }
        }
        if (route.webhookCode != null) {
            for (IamInternalClient.Webhook w : cfg.webhooks()) {
                Delivery d = row(e, EChannel.WEBHOOK, host(w.url()), EStatus.PENDING, null, now);
                d.setWebhookId(Long.valueOf(w.id()));
                rows.add(d);
            }
        }
        return rows;
    }

    /** "platform:" + 16 hex of SHA-256(lower-case address): finds the address again at send time, reveals none. */
    public static String platformRef(String address) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(address.trim().toLowerCase(java.util.Locale.ROOT)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return PLATFORM_RECIPIENT + java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Switched off for this event: a SKIPPED row, so the delivery log shows why nothing was sent. */
    private static Delivery chatRow(EventEnvelope e, EChannel channel, String target, boolean on, Instant now) {
        return on ? row(e, channel, target, EStatus.PENDING, null, now)
                : row(e, channel, target, EStatus.SKIPPED, CHANNEL_EVENT_DISABLED, now);
    }

    /** data.recipientPhone when it is E.164; null otherwise. */
    public static String phone(JsonNode data) {
        String v = data == null ? "" : data.path("recipientPhone").asText("").trim();
        return v.matches("\\+[1-9][0-9]{6,14}") ? v : null;
    }

    /** "order:&lt;orderId&gt;": the customer's address is read from order-service at send time (never in the event). */
    static List<String> orderRecipient(JsonNode data) {
        String id = data == null ? "" : data.path("orderId").asText("");
        return id.matches("[0-9]{1,19}") ? List.of(ORDER_RECIPIENT + id) : List.of();
    }

    /** "payload" for data.recipientEmail, "user:&lt;id&gt;" per data.recipientUserIds (distinct, in order). */
    static List<String> recipients(JsonNode data) {
        List<String> refs = new ArrayList<>();
        if (data == null) {
            return refs;
        }
        if (!data.path("recipientEmail").asText("").isBlank()) {
            refs.add(PAYLOAD_RECIPIENT);
        }
        for (JsonNode id : data.path("recipientUserIds")) {
            String ref = USER_RECIPIENT + id.asText();
            if (id.asText().matches("[0-9]{1,19}") && !refs.contains(ref)) {
                refs.add(ref);
            }
        }
        return refs;
    }

    private static Delivery row(EventEnvelope e, EChannel channel, String target, EStatus status, String code,
                                Instant now) {
        Delivery d = new Delivery();
        d.setCompanyId(e.companyId());
        d.setEventId(e.eventId());
        d.setEventType(e.eventType());
        d.setChannel(channel);
        d.setTarget(target);
        d.setStatus(status);
        d.setLastErrorCode(code);
        d.setNextAttemptAt(status == EStatus.PENDING ? now.plus(FIRST_SEND_GRACE) : null);
        return d;
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String json(EventEnvelope e) {
        try {
            return mapper.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            throw new AmqpRejectAndDontRequeueException("unserializable event");
        }
    }
}
