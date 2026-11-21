package com.terp.erp.iam.language;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Language Management (docs/analysis/06 §3). The label keys are the FE's: the super admin imports them (JSON
 * {key: value}). The keys of the default language (System Settings) are the full set; a language that misses a key
 * shows the default language's text for it. A new language starts as a copy of the default language, to translate.
 * Only the UI labels are translated here: API messages and emails stay vi/en/ko (en for any other language).
 */
@Slf4j
@Service
public class LanguageService {
    static final int MAX_IMPORT = 10_000;
    static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("[A-Za-z0-9_.\\-]{1,200}");
    static final int MAX_VALUE = 2000;

    private final LanguageRepository languages;
    private final LanguageLabelRepository labels;
    private final SettingsService settings;
    // The labels are read by every page load of every user; they change only when the super admin saves.
    private final Cache<String, Map<String, String>> published = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(100).build();

    public LanguageService(LanguageRepository languages, LanguageLabelRepository labels, SettingsService settings) {
        this.languages = languages;
        this.labels = labels;
        this.settings = settings;
    }

    public record LanguageDto(String id, String code, String name, String countryCode, boolean enabled,
                              @Schema(description = "vi/en/ko: never deleted") boolean system,
                              @Schema(description = "The default language of System Settings") boolean isDefault) {
    }

    public record LanguageForm(
            @Schema(description = "Language code, e.g. ja or zh-TW", example = "ja",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "[a-z]{2,3}(-[A-Z]{2})?") String code,
            @Schema(example = "日本語", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
            @Schema(description = "ISO country code for the flag", example = "JP") @Pattern(regexp = "^$|[A-Z]{2}")
            String countryCode) {
    }

    public record LanguageUpdateForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "^$|[A-Z]{2}") String countryCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled) {
    }

    public record LabelRow(String key, String value,
                           @Schema(description = "Text of the default language, for reference") String defaultValue,
                           @Schema(description = "false = no own text yet: the default language's is shown")
                           boolean translated) {
    }

    public record ImportResult(int added, int updated) {
    }

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public List<LanguageDto> enabled() {
        String def = defaultCode();
        return languages.findByEnabledTrueOrderByIdAsc().stream().map(l -> dto(l, def)).toList();
    }

    /** Every key of the default language, in this language where translated (else the default text). */
    @Transactional(readOnly = true)
    public Map<String, String> published(String code) {
        Map<String, String> hit = published.getIfPresent(code);
        if (hit != null) {
            return hit;
        }
        Language l = languages.findByCode(code).filter(x -> x.isEnabled())
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_027, code));
        Map<String, String> out = new TreeMap<>(values(base()));
        out.putAll(values(l));
        Map<String, String> frozen = java.util.Collections.unmodifiableMap(out);
        published.put(code, frozen);
        return frozen;
    }

    // ------------------------------------------------------------------ super admin

    @Transactional(readOnly = true)
    public List<LanguageDto> all() {
        String def = defaultCode();
        return languages.findAllByOrderByIdAsc().stream().map(l -> dto(l, def)).toList();
    }

    /** Created enabled, as a copy of the default language's labels. */
    @Transactional
    public LanguageDto create(LanguageForm f) {
        if (languages.existsByCode(f.code())) {
            throw ApiException.badRequest(EIamError.IAM_026, f.code());
        }
        Language l = new Language();
        l.setCode(f.code());
        l.setName(f.name().trim());
        l.setCountryCode(blank(f.countryCode()));
        l.setEnabled(true);
        l = languages.save(l);
        Long id = l.getId();
        List<LanguageLabel> copy = labels.findByLanguageId(base().getId()).stream().map(b -> {
            LanguageLabel c = new LanguageLabel();
            c.setLanguageId(id);
            c.setLabelKey(b.getLabelKey());
            c.setLabelValue(b.getLabelValue());
            return c;
        }).toList();
        labels.saveAll(copy);
        log.info("Language {} created with {} labels", l.getCode(), copy.size());
        return dto(l, defaultCode());
    }

    @Transactional
    public LanguageDto update(String code, LanguageUpdateForm f) {
        Language l = require(code);
        if (!f.enabled() && code.equals(defaultCode())) {
            throw ApiException.badRequest(EIamError.IAM_029);
        }
        l.setName(f.name().trim());
        l.setCountryCode(blank(f.countryCode()));
        l.setEnabled(f.enabled());
        published.invalidateAll();
        return dto(l, defaultCode());
    }

    @Transactional
    public void delete(String code) {
        Language l = require(code);
        if (l.isSystemLang()) {
            throw ApiException.badRequest(EIamError.IAM_028);
        }
        if (code.equals(defaultCode())) {
            throw ApiException.badRequest(EIamError.IAM_029);
        }
        labels.deleteByLanguage(l.getId());
        languages.delete(l);
        published.invalidateAll();
    }

    /** Manage Language: the full key set (default language) with this language's text; keyword on key or text. */
    @Transactional(readOnly = true)
    public Page<LabelRow> labels(String code, String keyword, Pageable page) {
        Language l = require(code);
        Map<String, String> base = values(base());
        Map<String, String> own = values(l);
        TreeSet<String> keys = new TreeSet<>(base.keySet());
        keys.addAll(own.keySet());
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<LabelRow> rows = keys.stream()
                .map(k -> new LabelRow(k, own.getOrDefault(k, base.get(k)), base.get(k), own.containsKey(k)))
                .filter(r -> kw.isEmpty() || r.key().toLowerCase(Locale.ROOT).contains(kw)
                        || (r.value() != null && r.value().toLowerCase(Locale.ROOT).contains(kw)))
                .toList();
        int from = (int) Math.min(page.getOffset(), rows.size());
        int to = Math.min(from + page.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), page, rows.size());
    }

    /**
     * Save Changes: the texts of keys that exist (in the default language or this one). An empty text removes this
     * language's own text (the default language's is shown again). Unknown keys are refused (IAM_030): new keys
     * come in through import.
     */
    @Transactional
    public ImportResult save(String code, Map<String, String> changes) {
        Language l = require(code);
        Map<String, String> base = values(base());
        Map<String, LanguageLabel> own = labels.findByLanguageId(l.getId()).stream()
                .collect(Collectors.toMap(x -> x.getLabelKey(), Function.identity()));
        for (String k : changes.keySet()) {
            if (!base.containsKey(k) && !own.containsKey(k)) {
                throw ApiException.badRequest(EIamError.IAM_030, k);
            }
        }
        int added = 0;
        int updated = 0;
        for (var e : changes.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            check(e.getKey(), v);
            LanguageLabel row = own.get(e.getKey());
            if (v.isEmpty()) {
                if (row != null) {
                    labels.delete(row);
                    updated++;
                }
            } else if (row == null) {
                labels.save(label(l.getId(), e.getKey(), v));
                added++;
            } else if (!row.getLabelValue().equals(v)) {
                row.setLabelValue(v);
                updated++;
            }
        }
        published.invalidateAll();
        return new ImportResult(added, updated);
    }

    /** Import of the FE's label file ({key: value}): adds new keys and replaces the texts of existing ones. */
    @Transactional
    public ImportResult importLabels(String code, Map<String, String> file) {
        Language l = require(code);
        if (file.isEmpty() || file.size() > MAX_IMPORT) {
            throw ApiException.badRequest(EIamError.IAM_030, file.isEmpty() ? "(empty)" : file.size() + " keys");
        }
        file.forEach((k, v) -> check(k, v == null ? "" : v));
        Map<String, LanguageLabel> own = labels.findByLanguageId(l.getId()).stream()
                .collect(Collectors.toMap(x -> x.getLabelKey(), Function.identity()));
        int added = 0;
        int updated = 0;
        for (var e : file.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            if (v.isEmpty()) {
                continue;
            }
            LanguageLabel row = own.get(e.getKey());
            if (row == null) {
                labels.save(label(l.getId(), e.getKey(), v));
                added++;
            } else if (!row.getLabelValue().equals(v)) {
                row.setLabelValue(v);
                updated++;
            }
        }
        published.invalidateAll();
        log.info("Imported labels into {}: {} added, {} updated", code, added, updated);
        return new ImportResult(added, updated);
    }

    /** System Settings → Default Language must be an enabled language. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String code) {
        return languages.findByCode(code).map(x -> x.isEnabled()).orElse(false);
    }

    // ------------------------------------------------------------------ helpers

    private static void check(String key, String value) {
        if (key == null || !KEY.matcher(key).matches() || value.length() > MAX_VALUE) {
            throw ApiException.badRequest(EIamError.IAM_030, key);
        }
    }

    private Language base() {
        String def = defaultCode();
        return languages.findByCode(def).or(() -> languages.findByCode("en"))
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_027, def));
    }

    private Map<String, String> values(Language l) {
        return labels.findByLanguageId(l.getId()).stream()
                .collect(Collectors.toMap(x -> x.getLabelKey(), x -> x.getLabelValue(), (a, b) -> a,
                        HashMap::new));
    }

    private Language require(String code) {
        return languages.findByCode(code).orElseThrow(() -> ApiException.notFound(EIamError.IAM_027, code));
    }

    private String defaultCode() {
        return settings.system().defaultLanguage();
    }

    private static LanguageLabel label(Long languageId, String key, String value) {
        LanguageLabel n = new LanguageLabel();
        n.setLanguageId(languageId);
        n.setLabelKey(key);
        n.setLabelValue(value);
        return n;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LanguageDto dto(Language l, String def) {
        return new LanguageDto(l.getId().toString(), l.getCode(), l.getName(), l.getCountryCode(), l.isEnabled(),
                l.isSystemLang(), l.getCode().equals(def));
    }
}
