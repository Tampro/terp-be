package com.terp.erp.notification.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.channel.ChatChannel;
import com.terp.erp.notification.channel.EmailChannel;
import com.terp.erp.notification.channel.GoogleCalendarChannel;
import com.terp.erp.notification.channel.MessageTemplates;
import com.terp.erp.notification.channel.SendResult;
import com.terp.erp.notification.channel.SmsChannel;
import com.terp.erp.notification.channel.WebhookChannel;
import com.terp.erp.notification.client.IamInternalClient;
import com.terp.erp.notification.client.IamInternalClient.NotificationConfig;
import com.terp.erp.notification.config.NotificationProperties;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.event.EEventRoute;
import com.terp.erp.notification.event.EPlatformRoute;
import com.terp.erp.notification.event.EventIntake;
import com.terp.erp.notification.event.ProcessedEvent;
import com.terp.erp.notification.event.ProcessedEventRepository;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends PENDING deliveries, one by one, outside any transaction (a network call must not hold a database
 * connection; each status update is its own save). The channel settings are read from IAM again at send time (secrets
 * are never stored here), once per event per run. Outcome: SENT; SKIPPED when the channel was switched off in the
 * meantime; otherwise attempts+1 and either PENDING with a back-off or FAILED (final code, or max attempts).
 * TODO(assumption): one instance of notification-service; several would need a lock on due rows (ShedLock or
 * SELECT … SKIP LOCKED) so the retry job cannot send a row twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDispatcher {
    record Context(EventEnvelope envelope, EEventRoute route, NotificationConfig config) {
    }

    /** A platform event's delivery (companyId null): platform settings only. */
    record PlatformContext(EventEnvelope envelope, EPlatformRoute route, IamInternalClient.PlatformConfig config) {
    }

    private final DeliveryRepository deliveries;
    private final ProcessedEventRepository processed;
    private final IamInternalClient iam;
    private final EmailChannel email;
    private final ChatChannel chat;
    private final WebhookChannel webhook;
    private final SmsChannel sms;
    private final GoogleCalendarChannel calendar;
    private final NotificationProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    // Setter-injected so the existing wiring stays as is; only customer-order emails need it.
    private com.terp.erp.notification.client.OrderInternalClient orders;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setOrders(com.terp.erp.notification.client.OrderInternalClient orders) {
        this.orders = orders;
    }

    public void dispatch(List<Long> ids) {
        Map<String, Optional<Context>> contexts = new HashMap<>();
        Map<String, Optional<PlatformContext>> platformContexts = new HashMap<>();
        for (Long id : ids) {
            Delivery d = id == null ? null : deliveries.findById(id).orElse(null);
            if (d == null || d.getStatus() != EStatus.PENDING) {
                continue;
            }
            if (d.getCompanyId() == null) {
                dispatchPlatform(d, platformContexts);
                deliveries.save(d);
                continue;
            }
            try {
                Optional<Context> ctx = contexts.get(d.getEventId());
                if (ctx == null || ctx.isEmpty()) {
                    ctx = context(d);
                    contexts.put(d.getEventId(), ctx);
                }
                apply(d, ctx.map(c -> send(d, c)).orElse(SendResult.fail("EVENT_UNKNOWN")));
            } catch (ApiException e) {
                // IAM unavailable: retry later (the context is not cached, so the next row asks again).
                contexts.remove(d.getEventId());
                apply(d, SendResult.retry("IAM_UNAVAILABLE"));
            } catch (RuntimeException e) {
                log.warn("Delivery {} failed unexpectedly: {}", d.getId(), e.getClass().getSimpleName());
                apply(d, SendResult.retry("INTERNAL_ERROR"));
            }
            deliveries.save(d);
        }
    }

    private void dispatchPlatform(Delivery d, Map<String, Optional<PlatformContext>> cache) {
        try {
            Optional<PlatformContext> ctx = cache.get(d.getEventId());
            if (ctx == null || ctx.isEmpty()) {
                ctx = platformContext(d);
                cache.put(d.getEventId(), ctx);
            }
            apply(d, ctx.map(c -> sendPlatform(d, c)).orElse(SendResult.fail("EVENT_UNKNOWN")));
        } catch (ApiException e) {
            cache.remove(d.getEventId());
            apply(d, SendResult.retry("IAM_UNAVAILABLE"));
        } catch (RuntimeException e) {
            log.warn("Delivery {} failed unexpectedly: {}", d.getId(), e.getClass().getSimpleName());
            apply(d, SendResult.retry("INTERNAL_ERROR"));
        }
    }

    private Optional<PlatformContext> platformContext(Delivery d) {
        Optional<ProcessedEvent> pe = processed.findById(d.getEventId());
        Optional<EPlatformRoute> route = EPlatformRoute.of(d.getEventType());
        if (pe.isEmpty() || route.isEmpty()) {
            return Optional.empty();
        }
        EventEnvelope env;
        try {
            env = mapper.readValue(pe.get().getEnvelope(), EventEnvelope.class);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        return Optional.of(new PlatformContext(env, route.get(),
                iam.platformConfig(route.get().emailCode, route.get().webhookCode)));
    }

    private SendResult sendPlatform(Delivery d, PlatformContext c) {
        var cfg = c.config();
        return switch (d.getChannel()) {
            case EMAIL -> {
                if (!cfg.emailEnabled()) {
                    yield skipped("EMAIL_DISABLED");
                }
                if (cfg.smtp() == null) {
                    yield skipped("NO_SMTP");
                }
                // The address is looked up again: one removed from the list in the meantime gets nothing.
                String to = cfg.recipients().stream()
                        .filter(a -> EventIntake.platformRef(a).equals(d.getRecipientRef())).findFirst().orElse(null);
                if (to == null) {
                    yield skipped("NO_RECIPIENT");
                }
                MessageTemplates.Message m = MessageTemplates.email(c.route().templateKey, cfg.defaultLanguage(),
                        c.envelope().data());
                yield email.send(cfg.smtp(), to, m.subject(), m.body());
            }
            case WEBHOOK -> cfg.webhooks().stream().filter(w -> String.valueOf(d.getWebhookId()).equals(w.id()))
                    .findFirst()
                    .map(w -> webhook.send(w.url(), w.secret(), c.route().webhookCode, c.route().webhookName,
                            EPlatformRoute.MODULE, c.envelope()))
                    .orElse(skipped("WEBHOOK_INACTIVE"));
            // Platform events have no chat / SMS channel (no platform Slack/Telegram/Twilio settings).
            default -> skipped("INTEGRATION_DISABLED");
        };
    }

    private Optional<Context> context(Delivery d) {
        Optional<ProcessedEvent> pe = processed.findById(d.getEventId());
        Optional<EEventRoute> route = EEventRoute.of(d.getEventType());
        if (pe.isEmpty() || route.isEmpty()) {
            return Optional.empty();
        }
        EventEnvelope env;
        try {
            env = mapper.readValue(pe.get().getEnvelope(), EventEnvelope.class);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        NotificationConfig cfg = iam.config(d.getCompanyId(), d.getEventType(), route.get().emailCode(env.data()),
                route.get().webhookCode);
        return Optional.of(new Context(env, route.get(), cfg));
    }

    private SendResult send(Delivery d, Context c) {
        NotificationConfig cfg = c.config();
        JsonNode data = c.envelope().data();
        String lang = cfg.defaultLanguage();
        return switch (d.getChannel()) {
            case EMAIL -> {
                if (!cfg.emailEnabled()) {
                    yield skipped("EMAIL_DISABLED");
                }
                if (cfg.smtp() == null) {
                    yield skipped("NO_SMTP");
                }
                String to;
                if (d.getRecipientRef() != null && d.getRecipientRef().startsWith(EventIntake.ORDER_RECIPIENT)) {
                    Optional<com.terp.erp.notification.client.OrderInternalClient.Facts> facts = orderFacts(d);
                    to = facts.map(com.terp.erp.notification.client.OrderInternalClient.Facts::email)
                            .filter(x -> !x.isBlank()).map(String::trim).orElse(null);
                    data = withFacts(data, facts.orElse(null));
                } else {
                    to = address(d, data);
                }
                if (to == null) {
                    yield skipped("NO_RECIPIENT");
                }
                d.setTarget(Targets.maskEmail(to));
                IamInternalClient.EmailOverride own = cfg.templatesOrNone().email();
                if (own != null && own.body() != null && !own.body().isBlank()) {
                    MessageTemplates.Message m = MessageTemplates.customEmail(own.subject(), own.body(), data);
                    yield email.send(cfg.smtp(), to, m.subject(), m.body(), true);
                }
                MessageTemplates.Message m = MessageTemplates.email(c.route().emailCode(c.envelope().data()), lang, data);
                yield email.send(cfg.smtp(), to, m.subject(), m.body());
            }
            case SLACK -> cfg.slack() == null ? skipped("INTEGRATION_DISABLED")
                    : !cfg.slackOn() ? skipped(EventIntake.CHANNEL_EVENT_DISABLED)
                    : chat.slack(cfg.slack().webhookUrl(), chatText(cfg.templatesOrNone().slack(), c, lang, data));
            case TELEGRAM -> cfg.telegram() == null ? skipped("INTEGRATION_DISABLED")
                    : !cfg.telegramOn() ? skipped(EventIntake.CHANNEL_EVENT_DISABLED)
                    : chat.telegram(cfg.telegram().botToken(), cfg.telegram().chatId(),
                    chatText(cfg.templatesOrNone().telegram(), c, lang, data));
            case SMS -> {
                if (cfg.twilio() == null) {
                    yield skipped("INTEGRATION_DISABLED");
                }
                if (!cfg.smsOn()) {
                    yield skipped(EventIntake.CHANNEL_EVENT_DISABLED);
                }
                String phone = EventIntake.phone(data);
                if (phone == null) {
                    yield skipped("NO_RECIPIENT");
                }
                yield sms.send(cfg.twilio(), phone, chatText(cfg.templatesOrNone().sms(), c, lang, data));
            }
            case WEBHOOK -> cfg.webhooks().stream().filter(w -> String.valueOf(d.getWebhookId()).equals(w.id()))
                    .findFirst()
                    .map(w -> webhook.send(w.url(), w.secret(), c.route(), c.envelope()))
                    .orElse(skipped("WEBHOOK_INACTIVE"));
            case GOOGLE_CALENDAR -> cfg.googleCalendar() == null || c.route().calendar == null
                    ? skipped("INTEGRATION_DISABLED")
                    : calendar.send(d.getCompanyId(), cfg.googleCalendar(), c.route().calendar,
                    c.envelope().aggregateId(), data);
        };
    }

    /** The company's own line for the channel when it wrote one, else the built-in line. */
    static String chatText(String own, Context c, String lang, JsonNode data) {
        return own != null && !own.isBlank() ? MessageTemplates.customChat(own, data)
                : MessageTemplates.chat(c.route(), lang, data);
    }

    private Optional<com.terp.erp.notification.client.OrderInternalClient.Facts> orderFacts(Delivery d) {
        if (orders == null) {
            return Optional.empty();
        }
        return orders.facts(d.getCompanyId(), Long.valueOf(d.getRecipientRef().substring(EventIntake.ORDER_RECIPIENT.length())));
    }

    /** The order's facts as template keys ({orderNo} {storeName} {recipientName} {total}), over the event's ids. */
    static JsonNode withFacts(JsonNode data, com.terp.erp.notification.client.OrderInternalClient.Facts f) {
        com.fasterxml.jackson.databind.node.ObjectNode n = data instanceof com.fasterxml.jackson.databind.node.ObjectNode o
                ? o.deepCopy() : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (f != null) {
            n.put("orderNo", f.orderNo());
            n.put("storeName", f.storeName());
            n.put("recipientName", f.recipientName());
            n.put("total", f.total() == null ? "" : f.total().stripTrailingZeros().toPlainString());
        }
        return n;
    }

    /** The recipient's address now: the event's own, or the IAM user's (an inactive or unknown user gets none). */
    private String address(Delivery d, JsonNode data) {
        String ref = d.getRecipientRef();
        if (EventIntake.PAYLOAD_RECIPIENT.equals(ref)) {
            String v = data == null ? "" : data.path("recipientEmail").asText("");
            return v.isBlank() ? null : v.trim();
        }
        if (ref != null && ref.startsWith(EventIntake.USER_RECIPIENT)) {
            return iam.contact(Long.valueOf(ref.substring(EventIntake.USER_RECIPIENT.length())))
                    .filter(IamInternalClient.Contact::active)
                    .map(IamInternalClient.Contact::email)
                    .filter(x -> !x.isBlank()).orElse(null);
        }
        return null;
    }

    /** A sentinel: the channel has nothing to send (not an error, not retried). */
    private static SendResult skipped(String code) {
        return new SendResult(false, "SKIP:" + code, false);
    }

    void apply(Delivery d, SendResult r) {
        Instant now = Instant.now(clock);
        if (r.code() != null && r.code().startsWith("SKIP:")) {
            d.setStatus(EStatus.SKIPPED);
            d.setLastErrorCode(r.code().substring(5));
            d.setNextAttemptAt(null);
            return;
        }
        d.setAttempts(d.getAttempts() + 1);
        if (r.sent()) {
            d.setStatus(EStatus.SENT);
            d.setSentAt(now);
            d.setLastErrorCode(null);
            d.setNextAttemptAt(null);
            return;
        }
        d.setLastErrorCode(r.code());
        if (r.retryable() && d.getAttempts() < props.maxAttempts()) {
            d.setNextAttemptAt(now.plus(props.backoff(d.getAttempts())));
        } else {
            d.setStatus(EStatus.FAILED);
            d.setNextAttemptAt(null);
        }
    }
}
