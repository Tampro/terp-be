package com.terp.erp.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.NotificationConfig;
import com.terp.erp.notification.delivery.Delivery;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.delivery.DeliveryRepository;
import com.terp.erp.notification.delivery.Targets;
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
    public static final String CHANNEL_EVENT_DISABLED = "CHANNEL_EVENT_DISABLED";

    private final ProcessedEventRepository processed;
    private final DeliveryRepository deliveries;
    private final IamInternalClient iam;
    private final ObjectMapper mapper;
    private final Clock clock;

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
        Optional<EEventRoute> route = EEventRoute.of(e.eventType());
        if (route.isEmpty() || e.companyId() == null) {
            processed.saveAndFlush(pe);
            return List.of();
        }
        NotificationConfig cfg = iam.config(e.companyId(), e.eventType(), route.get().emailCode, route.get().webhookCode);
        List<Delivery> rows = plan(e, route.get(), cfg, now);
        processed.saveAndFlush(pe);
        deliveries.saveAll(rows);
        return rows.stream().filter(d -> d.getStatus() == EStatus.PENDING).map(Delivery::getId).toList();
    }

    List<Delivery> plan(EventEnvelope e, EEventRoute route, NotificationConfig cfg, Instant now) {
        List<Delivery> rows = new ArrayList<>();
        if (route.emailCode != null) {
            List<String> refs = recipients(e.data());
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
        if (route.webhookCode != null) {
            for (IamInternalClient.Webhook w : cfg.webhooks()) {
                Delivery d = row(e, EChannel.WEBHOOK, host(w.url()), EStatus.PENDING, null, now);
                d.setWebhookId(Long.valueOf(w.id()));
                rows.add(d);
            }
        }
        return rows;
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
