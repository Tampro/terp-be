package com.terp.erp.iam.template;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.email.EEmailError;
import com.terp.erp.iam.email.ENotificationEvent;
import com.terp.erp.iam.email.ENotificationEvent.ENotificationModule;
import com.terp.erp.iam.email.EmailTemplateService;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.integration.EChannelEvent;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.template.CompanyNotificationTemplate.ETemplateChannel;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Company Settings (ERP manual 3.5): the company's own texts for the business emails (ENotificationEvent) and the
 * Slack / Telegram / SMS lines (EChannelEvent), per language. Edit only: the catalog is fixed; saving creates the
 * company's override, deleting it goes back to the built-in default of notification-service (which owns the default
 * texts, so a company that customises nothing gets exactly the texts it got before).
 */
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    public static final int CHAT_MAX = 1000;
    /** Twilio's limit for one message body. */
    public static final int SMS_MAX = 1600;

    private static final Safelist SAFE = Safelist.relaxed().addTags("span", "s", "hr")
            .addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
            .preserveRelativeLinks(false);
    private static final Document.OutputSettings OUT = new Document.OutputSettings().prettyPrint(false);

    private final CompanyNotificationTemplateRepository repo;
    private final CompanyIntegrationService integrations;

    // ------------------------------------------------------------------ DTOs

    @Schema(description = "One language of a template: customised=false means the built-in default is sent "
            + "(subject / body / text null)")
    public record LangContent(String lang, boolean customised, String subject, String body) {
    }

    public record EmailRow(String code, String name, ENotificationModule module, List<String> customisedLanguages,
                           boolean customised, List<String> variables) {
    }

    public record EmailGroup(ENotificationModule module, List<EmailRow> templates) {
    }

    public record EmailTemplateView(String code, String name, ENotificationModule module, List<String> variables,
                                    List<LangContent> languages) {
    }

    public record EmailTemplateForm(
            @Schema(description = "Subject; {key} placeholders from variables", example = "Ticket {ticketNumber}",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "HTML body (sanitised on save); {key} placeholders from variables",
                    example = "<p>Hello {name}, ticket {ticketNumber} is received.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 60000) String body) {
    }

    @Schema(description = "Optional: preview this text instead of the saved override")
    public record EmailPreviewForm(@Size(max = 255) String subject, @Size(max = 60000) String body) {
    }

    @Schema(description = "Rendered with sample values [key]; nothing is sent")
    public record EmailPreview(String subject, String body) {
    }

    public record ChatRow(String eventType, String name, List<String> customisedLanguages, List<String> variables) {
    }

    public record ChatGroup(EChannelEvent.EModule module, List<ChatRow> events) {
    }

    @Schema(description = "configured=false: the integration is not set up; the list is read-only until it is")
    public record ChatTemplatesView(String channel, boolean configured, List<ChatGroup> modules) {
    }

    public record ChatLang(String lang, boolean customised, String text) {
    }

    public record ChatTemplateView(String channel, boolean configured, String eventType, String name,
                                   EChannelEvent.EModule module, List<String> variables, List<ChatLang> languages) {
    }

    public record ChatTemplateForm(
            @Schema(description = "Plain text; {key} placeholders from variables. At most 1000 characters (Slack, "
                    + "Telegram) or 1600 (SMS)", example = "[ERP] Ticket {ticketNumber}: {subject}",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = SMS_MAX) String text) {
    }

    @Schema(description = "Optional: preview this text instead of the saved override")
    public record ChatPreviewForm(@Size(max = SMS_MAX) String text) {
    }

    public record ChatPreview(String text) {
    }

    /** For notification-service: the company's overrides for one event in one language; null = default. */
    public record EmailOverride(String subject, String body) {
    }

    public record Overrides(EmailOverride email, String slack, String telegram, String sms) {
    }

    // ------------------------------------------------------------------ email

    @Transactional(readOnly = true)
    public List<EmailGroup> emailList(Long requestedCompanyId) {
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        Map<String, List<String>> langs = langsByKey(cid, ETemplateChannel.EMAIL);
        Map<ENotificationModule, List<EmailRow>> by = new LinkedHashMap<>();
        for (ENotificationEvent e : ENotificationEvent.values()) {
            List<String> l = langs.getOrDefault(e.name(), List.of());
            by.computeIfAbsent(e.getModule(), k -> new ArrayList<>()).add(new EmailRow(e.name(), e.getDisplayName(),
                    e.getModule(), l, !l.isEmpty(), TemplateVariables.email(e)));
        }
        return by.entrySet().stream().map(x -> new EmailGroup(x.getKey(), x.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public EmailTemplateView emailGet(Long requestedCompanyId, String code) {
        ENotificationEvent e = emailCode(code);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return emailView(cid, e);
    }

    @Transactional
    public EmailTemplateView emailSave(Long requestedCompanyId, String code, String lang, EmailTemplateForm f) {
        ENotificationEvent e = emailCode(code);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        List<String> vars = TemplateVariables.email(e);
        String body = sanitize(f.body());
        checkPlaceholders(f.subject(), vars);
        checkPlaceholders(body, vars);
        CompanyNotificationTemplate t = row(cid, ETemplateChannel.EMAIL, e.name(), l);
        t.setSubject(f.subject().replaceAll("[\\r\\n]+", " ").trim());
        t.setBody(body);
        repo.save(t);
        return emailView(cid, e);
    }

    /** Reset to the built-in default (idempotent: no override = nothing to delete). */
    @Transactional
    public EmailTemplateView emailReset(Long requestedCompanyId, String code, String lang) {
        ENotificationEvent e = emailCode(code);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid, ETemplateChannel.EMAIL, e.name(), l)
                .ifPresent(repo::delete);
        return emailView(cid, e);
    }

    @Transactional(readOnly = true)
    public EmailPreview emailPreview(Long requestedCompanyId, String code, String lang, EmailPreviewForm f) {
        ENotificationEvent e = emailCode(code);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        String subject;
        String body;
        if (f != null && f.body() != null && !f.body().isBlank()) {
            subject = f.subject() == null ? "" : f.subject();
            body = sanitize(f.body());
        } else {
            CompanyNotificationTemplate t = repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid,
                            ETemplateChannel.EMAIL, e.name(), l)
                    .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_023));
            subject = t.getSubject();
            body = t.getBody();
        }
        Map<String, String> sample = TemplateVariables.samples(TemplateVariables.email(e));
        return new EmailPreview(TemplateVariables.fill(subject, sample, false).trim(),
                TemplateVariables.fill(body, sample, true));
    }

    // ------------------------------------------------------------------ chat

    @Transactional(readOnly = true)
    public ChatTemplatesView chatList(Long requestedCompanyId, String channelPath) {
        ETemplateChannel ch = chatChannel(channelPath);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        Map<String, List<String>> langs = langsByKey(cid, ch);
        Map<EChannelEvent.EModule, List<ChatRow>> by = new LinkedHashMap<>();
        for (EChannelEvent.EModule m : EChannelEvent.EModule.values()) {
            by.put(m, new ArrayList<>());
        }
        for (EChannelEvent e : EChannelEvent.values()) {
            by.get(e.getModule()).add(new ChatRow(e.getEventType(), e.getDisplayName(),
                    langs.getOrDefault(e.getEventType(), List.of()), TemplateVariables.chat(e.getEventType())));
        }
        List<ChatGroup> groups = by.entrySet().stream().filter(x -> !x.getValue().isEmpty())
                .map(x -> new ChatGroup(x.getKey(), x.getValue())).toList();
        return new ChatTemplatesView(channelPath, configured(cid, ch), groups);
    }

    @Transactional(readOnly = true)
    public ChatTemplateView chatGet(Long requestedCompanyId, String channelPath, String eventType) {
        ETemplateChannel ch = chatChannel(channelPath);
        EChannelEvent e = chatEvent(eventType);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return chatView(cid, ch, channelPath, e);
    }

    @Transactional
    public ChatTemplateView chatSave(Long requestedCompanyId, String channelPath, String eventType, String lang,
                                     ChatTemplateForm f) {
        ETemplateChannel ch = chatChannel(channelPath);
        EChannelEvent e = chatEvent(eventType);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        // TODO(assumption): manual 3.5 "the integration must be configured first" read as: list visible, edit refused.
        if (!configured(cid, ch)) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_022, channelPath);
        }
        String text = f.text().trim();
        int max = ch == ETemplateChannel.SMS ? SMS_MAX : CHAT_MAX;
        if (text.length() > max) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_024, channelPath, max);
        }
        checkPlaceholders(text, TemplateVariables.chat(e.getEventType()));
        CompanyNotificationTemplate t = row(cid, ch, e.getEventType(), l);
        t.setSubject(null);
        t.setBody(text);
        repo.save(t);
        return chatView(cid, ch, channelPath, e);
    }

    /** Reset is allowed even when the integration is off: it only removes the company's own text. */
    @Transactional
    public ChatTemplateView chatReset(Long requestedCompanyId, String channelPath, String eventType, String lang) {
        ETemplateChannel ch = chatChannel(channelPath);
        EChannelEvent e = chatEvent(eventType);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid, ch, e.getEventType(), l).ifPresent(repo::delete);
        return chatView(cid, ch, channelPath, e);
    }

    @Transactional(readOnly = true)
    public ChatPreview chatPreview(Long requestedCompanyId, String channelPath, String eventType, String lang,
                                   ChatPreviewForm f) {
        ETemplateChannel ch = chatChannel(channelPath);
        EChannelEvent e = chatEvent(eventType);
        String l = checkLang(lang);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        String text = f != null && f.text() != null && !f.text().isBlank() ? f.text()
                : repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid, ch, e.getEventType(), l)
                .map(CompanyNotificationTemplate::getBody)
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_023));
        return new ChatPreview(TemplateVariables.fill(text,
                TemplateVariables.samples(TemplateVariables.chat(e.getEventType())), false).trim());
    }

    // ------------------------------------------------------------------ internal (notification-service)

    /**
     * The company's overrides for one event in the language its messages are sent in (the company default
     * language; none = vi, as notification-service). A language without a row, or one outside vi / en / ko, = null.
     */
    @Transactional(readOnly = true)
    public Overrides overrides(Long companyId, String eventType, String emailCode, String language) {
        String l = language == null || language.isBlank() ? "vi"
                : language.toLowerCase(Locale.ROOT).split("[-_]")[0];
        if (!EmailTemplateService.LANGUAGES.contains(l)) {
            return new Overrides(null, null, null, null);
        }
        EmailOverride email = emailCode == null || emailCode.isBlank() ? null
                : repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(companyId, ETemplateChannel.EMAIL, emailCode, l)
                .map(t -> new EmailOverride(t.getSubject(), t.getBody())).orElse(null);
        return new Overrides(email, chatText(companyId, ETemplateChannel.SLACK, eventType, l),
                chatText(companyId, ETemplateChannel.TELEGRAM, eventType, l),
                chatText(companyId, ETemplateChannel.SMS, eventType, l));
    }

    private String chatText(Long cid, ETemplateChannel ch, String eventType, String lang) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        return repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid, ch, eventType, lang)
                .map(CompanyNotificationTemplate::getBody).orElse(null);
    }

    // ------------------------------------------------------------------ helpers

    static String sanitize(String html) {
        return Jsoup.clean(html == null ? "" : html, "", SAFE, OUT).trim();
    }

    private EmailTemplateView emailView(Long cid, ENotificationEvent e) {
        Map<String, CompanyNotificationTemplate> rows = byLang(cid, ETemplateChannel.EMAIL, e.name());
        List<LangContent> l = EmailTemplateService.LANGUAGES.stream().map(lang -> {
            CompanyNotificationTemplate t = rows.get(lang);
            return t == null ? new LangContent(lang, false, null, null)
                    : new LangContent(lang, true, t.getSubject(), t.getBody());
        }).toList();
        return new EmailTemplateView(e.name(), e.getDisplayName(), e.getModule(), TemplateVariables.email(e), l);
    }

    private ChatTemplateView chatView(Long cid, ETemplateChannel ch, String path, EChannelEvent e) {
        Map<String, CompanyNotificationTemplate> rows = byLang(cid, ch, e.getEventType());
        List<ChatLang> l = EmailTemplateService.LANGUAGES.stream().map(lang -> {
            CompanyNotificationTemplate t = rows.get(lang);
            return new ChatLang(lang, t != null, t == null ? null : t.getBody());
        }).toList();
        return new ChatTemplateView(path, configured(cid, ch), e.getEventType(), e.getDisplayName(), e.getModule(),
                TemplateVariables.chat(e.getEventType()), l);
    }

    private Map<String, CompanyNotificationTemplate> byLang(Long cid, ETemplateChannel ch, String key) {
        return repo.findByCompanyIdAndChannelAndTemplateKey(cid, ch, key).stream()
                .filter(t -> cid.equals(t.getCompanyId()))
                .collect(Collectors.toMap(CompanyNotificationTemplate::getLang, Function.identity(), (a, b) -> a));
    }

    private Map<String, List<String>> langsByKey(Long cid, ETemplateChannel ch) {
        return repo.findByCompanyIdAndChannel(cid, ch).stream().filter(t -> cid.equals(t.getCompanyId()))
                .collect(Collectors.groupingBy(CompanyNotificationTemplate::getTemplateKey, LinkedHashMap::new,
                        Collectors.mapping(CompanyNotificationTemplate::getLang,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        x -> x.stream().sorted().toList()))));
    }

    private CompanyNotificationTemplate row(Long cid, ETemplateChannel ch, String key, String lang) {
        return repo.findByCompanyIdAndChannelAndTemplateKeyAndLang(cid, ch, key, lang).orElseGet(() -> {
            CompanyNotificationTemplate n = new CompanyNotificationTemplate();
            n.setCompanyId(cid);
            n.setChannel(ch);
            n.setTemplateKey(key);
            n.setLang(lang);
            return n;
        });
    }

    private boolean configured(Long cid, ETemplateChannel ch) {
        return switch (ch) {
            case SLACK -> integrations.slackWebhookUrl(cid).isPresent();
            case TELEGRAM -> integrations.telegramTarget(cid).isPresent();
            case SMS -> integrations.twilioTarget(cid).isPresent();
            case EMAIL -> true;
        };
    }

    private static void checkPlaceholders(String text, List<String> allowed) {
        Optional<String> bad = TemplateVariables.unknown(text, allowed);
        if (bad.isPresent()) {
            throw ApiException.badRequest(ESettingsError.SETTINGS_025, "{" + bad.get() + "}");
        }
    }

    private static ENotificationEvent emailCode(String code) {
        return Arrays.stream(ENotificationEvent.values()).filter(x -> x.name().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound(EEmailError.EMAIL_001));
    }

    private static EChannelEvent chatEvent(String eventType) {
        return EChannelEvent.ofEventType(eventType)
                .orElseThrow(() -> ApiException.notFound(ESettingsError.SETTINGS_021, String.valueOf(eventType)));
    }

    private static ETemplateChannel chatChannel(String path) {
        return switch (path == null ? "" : path) {
            case "slack" -> ETemplateChannel.SLACK;
            case "telegram" -> ETemplateChannel.TELEGRAM;
            case "sms" -> ETemplateChannel.SMS;
            default -> throw ApiException.badRequest(ESettingsError.SETTINGS_020, String.valueOf(path));
        };
    }

    private static String checkLang(String lang) {
        if (lang == null || !EmailTemplateService.LANGUAGES.contains(lang)) {
            throw ApiException.badRequest(EEmailError.EMAIL_002, String.valueOf(lang));
        }
        return lang;
    }
}
