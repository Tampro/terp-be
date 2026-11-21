package com.terp.erp.employee.certificate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * NOC / Experience Certificate / Joining Letter settings (docs/analysis/06 §10.5.8-10.5.10): a company's own text per
 * letter and language; without one the built-in default is used.
 */
@Service
@RequiredArgsConstructor
public class LetterTemplateService {
    private final LetterTemplateRepository repo;

    public record LetterTemplateForm(
            @Schema(description = "Rich text (HTML). Only formatting tags are kept; of styles only text-align.",
                    example = "<p>This is to certify that <strong>{employee_name}</strong> ...</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100_000) String content) {
    }

    public record LetterTemplateView(ELetterType type, String lang, String title, String content,
                                     @Schema(description = "false = the built-in default text")
                                     boolean customized,
                                     @Schema(description = "Placeholders replaced when the letter is generated")
                                     List<String> placeholders,
                                     @Schema(description = "Language the letter is printed in: ko prints en until "
                                             + "a Korean font is bundled")
                                     String printedIn,
                                     Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public LetterTemplateView get(String typeCode, String lang, Long requestedCompanyId) {
        ELetterType type = type(typeCode);
        String l = lang(lang);
        Long companyId = company(requestedCompanyId);
        return repo.findByCompanyIdAndLetterTypeAndLang(companyId, type, l)
                .map(t -> view(type, l, t.getContent(), true, t.getUpdatedDate()))
                .orElseGet(() -> view(type, l, LetterTexts.body(type, l), false, null));
    }

    /** Full replacement of the company's text of this letter and language. */
    @Transactional
    public LetterTemplateView save(String typeCode, String lang, Long requestedCompanyId, LetterTemplateForm f) {
        ELetterType type = type(typeCode);
        String l = lang(lang);
        Long companyId = company(requestedCompanyId);
        LetterTemplate t = repo.findByCompanyIdAndLetterTypeAndLang(companyId, type, l).orElseGet(() -> {
            LetterTemplate n = new LetterTemplate();
            n.setCompanyId(companyId);
            n.setLetterType(type);
            n.setLang(l);
            return n;
        });
        t.setContent(LetterPdf.sanitize(f.content()));
        LetterTemplate saved = repo.saveAndFlush(t);
        return view(type, l, saved.getContent(), true, saved.getUpdatedDate());
    }

    /** Back to the built-in default text. */
    @Transactional
    public LetterTemplateView reset(String typeCode, String lang, Long requestedCompanyId) {
        ELetterType type = type(typeCode);
        String l = lang(lang);
        repo.findByCompanyIdAndLetterTypeAndLang(company(requestedCompanyId), type, l).ifPresent(repo::delete);
        return view(type, l, LetterTexts.body(type, l), false, null);
    }

    /** The body printed for the company: its own text in that language, else the default. */
    @Transactional(readOnly = true)
    public String body(Long companyId, ELetterType type, String printLang) {
        return repo.findByCompanyIdAndLetterTypeAndLang(companyId, type, printLang).map(t -> t.getContent())
                .orElseGet(() -> LetterTexts.body(type, printLang));
    }

    static ELetterType type(String code) {
        try {
            return ELetterType.valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(EEmployeeError.EMP_025, code);
        }
    }

    /** vi (default), en or ko. */
    static String lang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "vi";
        }
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (!LetterTexts.LANGS.contains(l)) {
            throw ApiException.badRequest(EEmployeeError.EMP_094, lang);
        }
        return l;
    }

    private static LetterTemplateView view(ELetterType type, String lang, String content, boolean customized,
                                           Instant updated) {
        return new LetterTemplateView(type, lang, LetterTexts.title(type, lang), content, customized,
                LetterTexts.placeholders(type), LetterTexts.printLang(lang), updated);
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
