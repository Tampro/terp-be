package com.terp.erp.iam.email;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Email Templates page of the super admin (docs/analysis/06 §3.3.1) and the rendering used for sending. */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    public static final List<String> LANGUAGES = List.of("vi", "en", "ko");

    private final EmailTemplateRepository templates;
    private final EmailTemplateContentRepository contents;
    private final SettingsService settings;

    @Value("${erp.app-url:http://localhost:5173}")
    private String appUrl;

    // ------------------------------------------------------------------ DTOs

    public record TemplateRow(String id, String code, String name, String fromName, Instant createdDate,
                              Instant updatedDate) {
        static TemplateRow of(EmailTemplate t) {
            return new TemplateRow(t.getId().toString(), t.getCode(), t.getName(), t.getFromName(), t.getCreatedDate(),
                    t.getUpdatedDate());
        }
    }

    public record ContentDto(String lang, String subject, String content, Instant updatedDate) {
    }

    @Schema(description = "Editor: settings, content per language (missing languages are absent), variables")
    public record TemplateDetail(TemplateRow template, List<ContentDto> contents, List<String> variables,
                                 boolean toggleableByCompany) {
    }

    public record FromNameForm(
            @Schema(description = "From Name; placeholders allowed", example = "{app_name}")
            @NotBlank @Size(max = 100) String fromName) {
    }

    public record ContentForm(
            @Schema(description = "Email Subject; placeholders allowed", example = "Welcome to {app_name}")
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "HTML body from the rich-text editor; placeholders allowed")
            @NotBlank @Size(max = 60000) String content) {
    }

    @Schema(description = "A template rendered with sample values")
    public record Rendered(String fromName, String subject, String html) {
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<TemplateRow> search(String keyword, Pageable pageable) {
        return templates.search(keyword == null ? "" : keyword.trim(), pageable).map(TemplateRow::of);
    }

    @Transactional(readOnly = true)
    public TemplateDetail get(Long id) {
        EmailTemplate t = require(id);
        List<ContentDto> c = contents.findByTemplateIdOrderByLang(id).stream()
                .map(x -> new ContentDto(x.getLang(), x.getSubject(), x.getContent(), x.getUpdatedDate())).toList();
        EEmailTemplate def = EEmailTemplate.valueOf(t.getCode());
        return new TemplateDetail(TemplateRow.of(t), c, def.allVariables(), def.isToggleable());
    }

    @Transactional
    public TemplateDetail saveFromName(Long id, String fromName) {
        require(id).setFromName(fromName.trim());
        return get(id);
    }

    @Transactional
    public TemplateDetail saveContent(Long id, String lang, ContentForm f) {
        require(id);
        String l = checkLang(lang);
        EmailTemplateContent c = contents.findByTemplateIdAndLang(id, l).orElseGet(() -> {
            EmailTemplateContent n = new EmailTemplateContent();
            n.setTemplateId(id);
            n.setLang(l);
            return n;
        });
        c.setSubject(f.subject().trim());
        c.setContent(f.content());
        contents.save(c);
        return get(id);
    }

    /** Preview with made-up values so the admin sees layout and placeholder typos. */
    @Transactional(readOnly = true)
    public Rendered preview(Long id, String lang) {
        EmailTemplate t = require(id);
        Map<String, String> sample = new HashMap<>();
        EEmailTemplate.valueOf(t.getCode()).getVariables().forEach(v -> sample.put(v, "[" + v + "]"));
        return render(t.getCode(), checkLang(lang), sample)
                .orElseThrow(() -> ApiException.badRequest(EEmailError.EMAIL_002, lang));
    }

    // ------------------------------------------------------------------ sending

    /**
     * Template + values → sender name, subject, HTML. Language: the requested one, else English, else any stored.
     * Common values (app name, app URL) are added here.
     */
    @Transactional(readOnly = true)
    public Optional<Rendered> render(String code, String lang, Map<String, String> values) {
        Optional<EmailTemplate> t = templates.findByCode(code);
        if (t.isEmpty()) {
            return Optional.empty();
        }
        List<EmailTemplateContent> all = contents.findByTemplateIdOrderByLang(t.get().getId());
        Optional<EmailTemplateContent> c = all.stream().filter(x -> x.getLang().equals(lang)).findFirst()
                .or(() -> all.stream().filter(x -> x.getLang().equals("en")).findFirst())
                .or(() -> all.stream().findFirst());
        if (c.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> v = new HashMap<>(values);
        v.put("app_name", settings.brand().titleText());
        v.put("app_url", appUrl);
        return Optional.of(new Rendered(TemplateRenderer.line(t.get().getFromName(), v),
                TemplateRenderer.line(c.get().getSubject(), v), TemplateRenderer.html(c.get().getContent(), v)));
    }

    private EmailTemplate require(Long id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound(EEmailError.EMAIL_001));
    }

    private static String checkLang(String lang) {
        if (lang == null || !LANGUAGES.contains(lang)) {
            throw ApiException.badRequest(EEmailError.EMAIL_002, String.valueOf(lang));
        }
        return lang;
    }
}
