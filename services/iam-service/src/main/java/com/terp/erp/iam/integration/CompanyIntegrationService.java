package com.terp.erp.iam.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.integration.IntegrationDtos.AiForm;
import com.terp.erp.iam.integration.IntegrationDtos.AiStored;
import com.terp.erp.iam.integration.IntegrationDtos.AiView;
import com.terp.erp.iam.integration.IntegrationDtos.EIntegration;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarForm;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarStored;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarView;
import com.terp.erp.iam.integration.IntegrationDtos.IntegrationsView;
import com.terp.erp.iam.integration.IntegrationDtos.SlackForm;
import com.terp.erp.iam.integration.IntegrationDtos.SlackStored;
import com.terp.erp.iam.integration.IntegrationDtos.SlackView;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramForm;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramStored;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramView;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioForm;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioStored;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioView;
import com.terp.erp.iam.settings.CompanySetting;
import com.terp.erp.iam.settings.CompanySettingRepository;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Company integrations (ERP manual 19). Kept in iam-service next to the other company settings: they are a few
 * rows of company configuration, with the same permission, encryption key (ERP_SETTINGS_ENC_KEY through
 * {@link SecretCipher}) and table (company_setting, one section per integration). Secrets are stored encrypted and
 * never returned. Slack / Telegram / Twilio are read (decrypted) by notification-service through the internal
 * notification-config, Google Calendar too (calendar sync of holidays / sales invoices); TODO: AI assistant has no
 * consumer yet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyIntegrationService {
    static final String SLACK_HOST = "hooks.slack.com";
    static final String TELEGRAM_HOST = "api.telegram.org";

    private final CompanySettingRepository repo;
    private final SecretCipher cipher;
    private final ObjectMapper mapper;
    private final UrlGuard guard;
    private final OutboundHttp http;

    @Transactional(readOnly = true)
    public IntegrationsView all(Long requestedCompanyId) {
        Long cid = company(requestedCompanyId);
        return new IntegrationsView(slack(cid), telegram(cid), twilio(cid), ai(cid), googleCalendar(cid));
    }

    // ------------------------------------------------------------------ Slack

    @Transactional
    public SlackView saveSlack(Long requestedCompanyId, SlackForm f) {
        Long cid = company(requestedCompanyId);
        SlackStored old = read(cid, EIntegration.SLACK, SlackStored.class).orElse(new SlackStored(false, null));
        String url = blank(f.webhookUrl());
        if (url != null) {
            guard.checkSyntax(url, SLACK_HOST);
        }
        String enc = url != null ? cipher.encrypt(url) : old.webhookUrlEnc();
        requireFor(f.enabled(), enc, "Slack", "webhookUrl");
        write(cid, EIntegration.SLACK, new SlackStored(f.enabled(), enc));
        return slack(cid);
    }

    /** Posts one sample message to the stored webhook URL; only to hooks.slack.com. */
    @Transactional(readOnly = true)
    public TestResult testSlack(Long requestedCompanyId) {
        Long cid = company(requestedCompanyId);
        SlackStored s = read(cid, EIntegration.SLACK, SlackStored.class)
                .filter(x -> x.webhookUrlEnc() != null)
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_017, "Slack"));
        var uri = guard.check(cipher.decrypt(s.webhookUrlEnc()), SLACK_HOST);
        return TestResult.of(http.postJson(uri, json(Map.of("text", "ERP test message: the Slack integration works.")),
                Map.of()));
    }

    // ------------------------------------------------------------------ Telegram

    @Transactional
    public TelegramView saveTelegram(Long requestedCompanyId, TelegramForm f) {
        Long cid = company(requestedCompanyId);
        TelegramStored old = read(cid, EIntegration.TELEGRAM, TelegramStored.class)
                .orElse(new TelegramStored(false, null, null));
        String token = blank(f.botToken());
        String enc = token != null ? cipher.encrypt(token) : old.botTokenEnc();
        String chatId = blank(f.chatId());
        requireFor(f.enabled(), enc, "Telegram", "botToken");
        requireFor(f.enabled(), chatId, "Telegram", "chatId");
        write(cid, EIntegration.TELEGRAM, new TelegramStored(f.enabled(), enc, chatId));
        return telegram(cid);
    }

    /** sendMessage to the stored chat; only to api.telegram.org. */
    @Transactional(readOnly = true)
    public TestResult testTelegram(Long requestedCompanyId) {
        Long cid = company(requestedCompanyId);
        TelegramStored s = read(cid, EIntegration.TELEGRAM, TelegramStored.class)
                .filter(x -> x.botTokenEnc() != null && x.chatId() != null)
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_017, "Telegram"));
        String token = cipher.decrypt(s.botTokenEnc());
        if (!token.matches("[0-9]{3,20}:[A-Za-z0-9_-]{20,80}")) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_019, "botToken");
        }
        var uri = guard.check("https://" + TELEGRAM_HOST + "/bot" + token + "/sendMessage", TELEGRAM_HOST);
        return TestResult.of(http.postJson(uri, json(Map.of("chat_id", s.chatId(),
                "text", "ERP test message: the Telegram integration works.")), Map.of()));
    }

    // ------------------------------------------------------------------ Twilio, AI assistant, Google Calendar

    @Transactional
    public TwilioView saveTwilio(Long requestedCompanyId, TwilioForm f) {
        Long cid = company(requestedCompanyId);
        TwilioStored old = read(cid, EIntegration.TWILIO, TwilioStored.class)
                .orElse(new TwilioStored(false, null, null, null));
        String token = blank(f.authToken());
        String enc = token != null ? cipher.encrypt(token) : old.authTokenEnc();
        String sid = blank(f.accountSid());
        String from = blank(f.fromNumber());
        requireFor(f.enabled(), sid, "Twilio", "accountSid");
        requireFor(f.enabled(), enc, "Twilio", "authToken");
        requireFor(f.enabled(), from, "Twilio", "fromNumber");
        write(cid, EIntegration.TWILIO, new TwilioStored(f.enabled(), sid, enc, from));
        return twilio(cid);
    }

    @Transactional
    public AiView saveAi(Long requestedCompanyId, AiForm f) {
        Long cid = company(requestedCompanyId);
        AiStored old = read(cid, EIntegration.AI_ASSISTANT, AiStored.class).orElse(new AiStored(false, null, null, null));
        String key = blank(f.apiKey());
        String enc = key != null ? cipher.encrypt(key) : old.apiKeyEnc();
        String model = blank(f.model());
        requireFor(f.enabled(), f.provider(), "AI assistant", "provider");
        requireFor(f.enabled(), enc, "AI assistant", "apiKey");
        requireFor(f.enabled(), model, "AI assistant", "model");
        write(cid, EIntegration.AI_ASSISTANT, new AiStored(f.enabled(), f.provider(), enc, model));
        return ai(cid);
    }

    @Transactional
    public GoogleCalendarView saveGoogleCalendar(Long requestedCompanyId, GoogleCalendarForm f) {
        Long cid = company(requestedCompanyId);
        GoogleCalendarStored old = read(cid, EIntegration.GOOGLE_CALENDAR, GoogleCalendarStored.class)
                .orElse(new GoogleCalendarStored(false, null, null, null));
        String json = blank(f.serviceAccountJson());
        String enc = old.serviceAccountJsonEnc();
        String email = old.serviceAccountEmail();
        if (json != null) {
            email = serviceAccountEmail(json);
            enc = cipher.encrypt(json);
        }
        String calendarId = blank(f.calendarId());
        requireFor(f.enabled(), calendarId, "Google Calendar", "calendarId");
        requireFor(f.enabled(), enc, "Google Calendar", "serviceAccountJson");
        write(cid, EIntegration.GOOGLE_CALENDAR, new GoogleCalendarStored(f.enabled(), calendarId, enc, email));
        return googleCalendar(cid);
    }

    /** Removes the integration and its secrets. Idempotent. */
    @Transactional
    public void delete(Long requestedCompanyId, String type) {
        EIntegration t = Arrays.stream(EIntegration.values()).filter(x -> x.path.equals(type)).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_018, type));
        Long cid = company(requestedCompanyId);
        repo.findByCompanyIdAndSection(cid, t.section).ifPresent(repo::delete);
    }

    // ------------------------------------------------------------------ for notification-service (internal)

    /** Decrypted Slack webhook URL when Slack is on for the company; empty otherwise. */
    @Transactional(readOnly = true)
    public Optional<String> slackWebhookUrl(Long companyId) {
        return read(companyId, EIntegration.SLACK, SlackStored.class)
                .filter(s -> s.enabled() && s.webhookUrlEnc() != null)
                .map(s -> cipher.decrypt(s.webhookUrlEnc()));
    }

    public record TelegramTarget(String botToken, String chatId) {
    }

    /** Decrypted bot token + chat id when Telegram is on for the company; empty otherwise. */
    @Transactional(readOnly = true)
    public Optional<TelegramTarget> telegramTarget(Long companyId) {
        return read(companyId, EIntegration.TELEGRAM, TelegramStored.class)
                .filter(s -> s.enabled() && s.botTokenEnc() != null && s.chatId() != null)
                .map(s -> new TelegramTarget(cipher.decrypt(s.botTokenEnc()), s.chatId()));
    }

    public record TwilioTarget(String accountSid, String authToken, String fromNumber) {
    }

    /** Decrypted Twilio credentials when SMS is on for the company; empty otherwise. */
    @Transactional(readOnly = true)
    public Optional<TwilioTarget> twilioTarget(Long companyId) {
        return read(companyId, EIntegration.TWILIO, TwilioStored.class)
                .filter(s -> s.enabled() && s.accountSid() != null && s.authTokenEnc() != null
                        && s.fromNumber() != null)
                .map(s -> new TwilioTarget(s.accountSid(), cipher.decrypt(s.authTokenEnc()), s.fromNumber()));
    }

    public record GoogleCalendarTarget(String calendarId, String serviceAccountJson) {
    }

    /** Calendar id + decrypted service account key file when Google Calendar is on for the company; empty otherwise. */
    @Transactional(readOnly = true)
    public Optional<GoogleCalendarTarget> googleCalendarTarget(Long companyId) {
        return read(companyId, EIntegration.GOOGLE_CALENDAR, GoogleCalendarStored.class)
                .filter(s -> s.enabled() && s.calendarId() != null && s.serviceAccountJsonEnc() != null)
                .map(s -> new GoogleCalendarTarget(s.calendarId(), cipher.decrypt(s.serviceAccountJsonEnc())));
    }

    // ------------------------------------------------------------------ views

    private SlackView slack(Long cid) {
        return read(cid, EIntegration.SLACK, SlackStored.class)
                .map(s -> new SlackView(true, s.enabled(), s.webhookUrlEnc() != null))
                .orElse(new SlackView(false, false, false));
    }

    private TelegramView telegram(Long cid) {
        return read(cid, EIntegration.TELEGRAM, TelegramStored.class)
                .map(s -> new TelegramView(true, s.enabled(), s.chatId(), s.botTokenEnc() != null))
                .orElse(new TelegramView(false, false, null, false));
    }

    private TwilioView twilio(Long cid) {
        return read(cid, EIntegration.TWILIO, TwilioStored.class)
                .map(s -> new TwilioView(true, s.enabled(), s.accountSid(), s.fromNumber(), s.authTokenEnc() != null))
                .orElse(new TwilioView(false, false, null, null, false));
    }

    private AiView ai(Long cid) {
        return read(cid, EIntegration.AI_ASSISTANT, AiStored.class)
                .map(s -> new AiView(true, s.enabled(), s.provider(), s.model(), s.apiKeyEnc() != null))
                .orElse(new AiView(false, false, null, null, false));
    }

    private GoogleCalendarView googleCalendar(Long cid) {
        return read(cid, EIntegration.GOOGLE_CALENDAR, GoogleCalendarStored.class)
                .map(s -> new GoogleCalendarView(true, s.enabled(), s.calendarId(), s.serviceAccountEmail(),
                        s.serviceAccountJsonEnc() != null))
                .orElse(new GoogleCalendarView(false, false, null, null, false));
    }

    // ------------------------------------------------------------------ helpers

    /** A Google key file: {"type":"service_account","client_email":…,"private_key":…}. */
    private String serviceAccountEmail(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            if (n == null || !"service_account".equals(n.path("type").asText())
                    || n.path("client_email").asText("").isBlank() || n.path("private_key").asText("").isBlank()) {
                throw ApiException.badRequest(ESettingsError.SETTINGS_016);
            }
            return n.path("client_email").asText();
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_016);
        }
    }

    private static void requireFor(boolean enabled, Object value, String integration, String field) {
        if (enabled && value == null) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_012, integration, field);
        }
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private <T> Optional<T> read(Long cid, EIntegration t, Class<T> type) {
        return repo.findByCompanyIdAndSection(cid, t.section).map(row -> {
            try {
                return mapper.readValue(row.getValueJson(), type);
            } catch (JsonProcessingException e) {
                log.error("Company {} integration {} unreadable", cid, t.section);
                return null;
            }
        });
    }

    private void write(Long cid, EIntegration t, Object value) {
        CompanySetting row = repo.findByCompanyIdAndSection(cid, t.section).orElseGet(() -> {
            CompanySetting n = new CompanySetting();
            n.setCompanyId(cid);
            n.setSection(t.section);
            return n;
        });
        row.setValueJson(json(value));
        repo.save(row);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("not serializable", e);
        }
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EIamError.IAM_003);
    }
}
