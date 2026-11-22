package com.terp.erp.recruitment.offertemplate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.catalog.CatalogService;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.common.HtmlSanitizer;
import com.terp.erp.recruitment.common.Texts;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offer Templates (docs/analysis/06 §7.1.16). Content is sanitized HTML; variables are the declared names (lowercase,
 * digits, underscore). Generate Offer fills every declared variable (HTML-escaped) and returns the letter without
 * saving it (PO, 2026-11-20). A placeholder that is not declared stays as written.
 */
@Service
@RequiredArgsConstructor
public class OfferTemplateService {
    static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-z][a-z0-9_]{0,63})\\s*}}");
    static final int PREVIEW = 150;

    private final OfferTemplateRepository templates;

    public record TemplateDto(String id, String name, String content, String contentPreview, List<String> variables,
                              EStatus status, Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record TemplateForm(
            @Schema(example = "Standard Offer", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150)
            String name,
            @Schema(example = "<p>Dear {{candidate_name}}, we offer you {{position}}.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200000) String content,
            @Schema(description = "Comma-separated names without braces", example = "candidate_name, position")
            @Size(max = 2000) String variables,
            @Schema(description = "Omitted = ACTIVE") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record GenerateForm(
            @Schema(description = "Value of every declared variable", example = "{\"candidate_name\": \"Test Person\"}")
            Map<String, String> values) {
    }

    public record Generated(String templateId, String name, String content) {
    }

    @Transactional(readOnly = true)
    public Page<TemplateDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        return templates.search(CatalogService.company(requestedCompanyId), status, Texts.kw(keyword), pageable)
                .map(OfferTemplateService::dto);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : templates.countByStatus(CatalogService.company(requestedCompanyId), Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public TemplateDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public TemplateDto create(Long requestedCompanyId, TemplateForm form) {
        OfferTemplate t = new OfferTemplate();
        t.setCompanyId(CatalogService.company(requestedCompanyId));
        apply(t, form, 0L);
        return dto(templates.save(t));
    }

    /** Full replacement. */
    @Transactional
    public TemplateDto update(Long id, TemplateForm form) {
        OfferTemplate t = require(id);
        apply(t, form, t.getId());
        return dto(t);
    }

    @Transactional
    public TemplateDto changeStatus(Long id, EStatus status) {
        OfferTemplate t = require(id);
        t.setStatus(status);
        return dto(t);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** Works on inactive templates too (preview). */
    @Transactional(readOnly = true)
    public Generated generate(Long id, Map<String, String> values) {
        OfferTemplate t = require(id);
        Map<String, String> given = values == null ? Map.of() : values;
        for (String v : t.getVariables()) {
            if (Texts.blankToNull(given.get(v)) == null) {
                throw ApiException.badRequest(ERecruitmentError.REC_034, v);
            }
        }
        return new Generated(t.getId().toString(), t.getName(), fill(t.getContent(), Set.copyOf(t.getVariables()),
                given));
    }

    static String fill(String content, Set<String> declared, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(content);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = declared.contains(name) ? HtmlUtils.htmlEscape(values.get(name).trim()) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** "a, b,,a" → [a, b]; each must be a valid name (REC_033). */
    static List<String> parseVariables(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String v = part.trim();
                if (v.isEmpty()) {
                    continue;
                }
                if (!NAME.matcher(v).matches()) {
                    throw ApiException.badRequest(ERecruitmentError.REC_033, v);
                }
                out.add(v);
            }
        }
        return List.copyOf(out);
    }

    private void apply(OfferTemplate t, TemplateForm f, Long selfId) {
        String name = f.name().trim();
        if (templates.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(ERecruitmentError.REC_004);
        }
        t.setName(name);
        t.setContent(HtmlSanitizer.clean(f.content()));
        t.getVariables().clear();
        t.getVariables().addAll(parseVariables(f.variables()));
        if (f.status() != null) {
            t.setStatus(f.status());
        }
    }

    private static TemplateDto dto(OfferTemplate t) {
        return new TemplateDto(t.getId().toString(), t.getName(), t.getContent(),
                HtmlSanitizer.preview(t.getContent(), PREVIEW), List.copyOf(t.getVariables()), t.getStatus(),
                t.getCreatedDate(), t.getUpdatedDate());
    }

    /**
     * The letter of an offer: declared variables filled from values (HTML-escaped); a declared variable without a
     * value is left empty. TODO(assumption): an unknown variable prints empty rather than refusing the download.
     */
    @Transactional(readOnly = true)
    public Generated letter(Long id, Long companyId, Map<String, String> values) {
        OfferTemplate t = require(id);
        if (!t.getCompanyId().equals(companyId)) {
            throw ApiException.notFound(ERecruitmentError.REC_032);
        }
        Map<String, String> all = new java.util.HashMap<>();
        for (String v : t.getVariables()) {
            String given = values.get(v);
            all.put(v, given == null ? "" : given);
        }
        return new Generated(t.getId().toString(), t.getName(), fill(t.getContent(), Set.copyOf(t.getVariables()), all));
    }

    public OfferTemplate require(Long id) {
        OfferTemplate t = templates.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ERecruitmentError.REC_032));
        TenantGuard.assertSameCompany(t.getCompanyId(), ERecruitmentError.REC_032);
        return t;
    }
}
