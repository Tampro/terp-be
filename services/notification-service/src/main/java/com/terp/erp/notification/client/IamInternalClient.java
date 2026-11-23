package com.terp.erp.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.config.NotificationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * IAM's internal endpoints with the service-account token (GET only, so one retry is safe). Answers hold decrypted
 * secrets: never log a body. IAM unreachable = NTF_004 (503), so the broker message or the delivery is retried.
 */
@Slf4j
@Component
public class IamInternalClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Smtp(String host, Integer port, String username, String password, String encryption,
                       String fromAddress, String fromName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slack(String webhookUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Telegram(String botToken, String chatId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Twilio(String accountSid, String authToken, String fromNumber) {
    }

    /** serviceAccountJson: the Google key file, private key included: never logged (toString hides it). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleCalendar(String calendarId, String serviceAccountJson) {
        @Override
        public String toString() {
            return "GoogleCalendar[calendarId=" + calendarId + ", serviceAccountJson=***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Webhook(String id, String url, String secret) {
    }

    /** The company's own email text (subject + sanitised HTML body); null = built-in default. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmailOverride(String subject, String body) {
    }

    /** The company's own texts for the event in defaultLanguage; each part null = built-in default. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Templates(EmailOverride email, String slack, String telegram, String sms) {
    }

    /**
     * …EnabledForEvent: the company's per-event switch of that chat channel; null (an IAM without the switch) = on.
     * twilio: null when SMS is off. templates: null (an IAM without company templates) = every default.
     * googleCalendar: null when the Google Calendar integration is off (or an IAM without it).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationConfig(String companyId, String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                     Slack slack, Telegram telegram, Twilio twilio, List<Webhook> webhooks,
                                     Boolean slackEnabledForEvent, Boolean telegramEnabledForEvent,
                                     Boolean smsEnabledForEvent, Templates templates, GoogleCalendar googleCalendar) {
        public NotificationConfig(String companyId, String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                  Slack slack, Telegram telegram, Twilio twilio, List<Webhook> webhooks,
                                  Boolean slackEnabledForEvent, Boolean telegramEnabledForEvent,
                                  Boolean smsEnabledForEvent, Templates templates) {
            this(companyId, defaultLanguage, emailEnabled, smtp, slack, telegram, twilio, webhooks,
                    slackEnabledForEvent, telegramEnabledForEvent, smsEnabledForEvent, templates, null);
        }

        public NotificationConfig(String companyId, String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                  Slack slack, Telegram telegram, Twilio twilio, List<Webhook> webhooks,
                                  Boolean slackEnabledForEvent, Boolean telegramEnabledForEvent,
                                  Boolean smsEnabledForEvent) {
            this(companyId, defaultLanguage, emailEnabled, smtp, slack, telegram, twilio, webhooks,
                    slackEnabledForEvent, telegramEnabledForEvent, smsEnabledForEvent, null, null);
        }

        public Templates templatesOrNone() {
            return templates == null ? new Templates(null, null, null, null) : templates;
        }

        public List<Webhook> webhooks() {
            return webhooks == null ? List.of() : webhooks;
        }

        public boolean slackOn() {
            return !Boolean.FALSE.equals(slackEnabledForEvent);
        }

        public boolean telegramOn() {
            return !Boolean.FALSE.equals(telegramEnabledForEvent);
        }

        public boolean smsOn() {
            return !Boolean.FALSE.equals(smsEnabledForEvent);
        }
    }

    /** Platform event settings (companyId null): never a company's. recipients = the platform team addresses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlatformConfig(String defaultLanguage, boolean emailEnabled, Smtp smtp, List<String> recipients,
                                 List<Webhook> webhooks) {
        public List<String> recipients() {
            return recipients == null ? List.of() : recipients;
        }

        public List<Webhook> webhooks() {
            return webhooks == null ? List.of() : webhooks;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(String id, String fullName, String email, String companyId, boolean active) {
    }

    private final RestClient client;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public IamInternalClient(NotificationProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(props.iamServiceUrl())).requestFactory(rf).build();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** eventCode / webhookEvent may be null; eventType (routing key) selects the per-event chat switches. */
    public NotificationConfig config(Long companyId, String eventType, String eventCode, String webhookEvent) {
        JsonNode data = get("/api/v1/internal/notification-config?companyId={c}&eventType={t}&eventCode={e}"
                        + "&webhookEvent={w}",
                companyId, eventType == null ? "" : eventType, eventCode == null ? "" : eventCode,
                webhookEvent == null ? "" : webhookEvent)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004));
        return mapper.convertValue(data, NotificationConfig.class);
    }

    /** emailCode (EPlatformEmail) / webhookEvent (EPlatformWebhookEvent) may be null. */
    public PlatformConfig platformConfig(String emailCode, String webhookEvent) {
        JsonNode data = get("/api/v1/internal/platform-notification-config?emailCode={e}&webhookEvent={w}",
                emailCode == null ? "" : emailCode, webhookEvent == null ? "" : webhookEvent)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004));
        return mapper.convertValue(data, PlatformConfig.class);
    }

    /** Empty when IAM does not know the user (404). */
    public Optional<Contact> contact(Long userId) {
        return get("/api/v1/internal/users/{id}/contact", userId).map(d -> mapper.convertValue(d, Contact.class));
    }

    private Optional<JsonNode> get(String uri, Object... vars) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode body = client.get().uri(uri, vars)
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(JsonNode.class);
                return Optional.ofNullable(body == null ? null : body.path("data"));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (HttpClientErrorException e) {
                // 400 / 401 / 403 will not get better by retrying: a configuration problem.
                log.error("IAM internal call refused: HTTP {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
            } catch (RestClientException e) {
                last = e;
            }
        }
        log.warn("IAM internal call failed: {}", last.getClass().getSimpleName());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
    }
}
