package com.terp.erp.support.kb;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.HtmlSanitizer;
import com.terp.erp.support.common.Texts;
import com.terp.erp.support.kb.CsvImport.ImportResult;
import com.terp.erp.support.kb.CsvImport.Row;
import com.terp.erp.support.kb.CsvImport.RowError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** FAQs (reference FaqController: title, description; CSV import). */
@Service
@RequiredArgsConstructor
public class FaqService {
    public static final List<String> IMPORT_HEADER = List.of("title", "description");

    public record FaqDto(String id, String title, String description, boolean published, Instant createdDate,
                         Instant updatedDate) {
        static FaqDto of(Faq f) {
            return new FaqDto(String.valueOf(f.getId()), f.getTitle(), f.getDescription(), f.isPublished(),
                    f.getCreatedDate(), f.getUpdatedDate());
        }
    }

    public record FaqForm(
            @Schema(description = "The question", example = "How do I track my ticket?",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String title,
            @Schema(description = "The answer, HTML; sanitized", example = "<p>Use your ticket number and email.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100_000) String description,
            @Schema(description = "Shown on the public portal") boolean published) {
    }

    private final FaqRepository faqs;
    private final RestExceptionHandler messages;

    @Transactional(readOnly = true)
    public Page<FaqDto> list(Long requestedCompanyId, Boolean published, String keyword, Pageable pageable) {
        return listOf(Texts.company(requestedCompanyId), published, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FaqDto> listOf(Long companyId, Boolean published, String keyword, Pageable pageable) {
        return faqs.search(companyId, published, Texts.kw(keyword), pageable).map(FaqDto::of);
    }

    @Transactional(readOnly = true)
    public FaqDto get(Long id) {
        return FaqDto.of(require(id));
    }

    @Transactional
    public FaqDto create(Long requestedCompanyId, FaqForm f) {
        Faq q = new Faq();
        q.setCompanyId(Texts.company(requestedCompanyId));
        apply(q, f);
        return FaqDto.of(faqs.save(q));
    }

    @Transactional
    public FaqDto update(Long id, FaqForm f) {
        Faq q = require(id);
        apply(q, f);
        return FaqDto.of(q);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** All-or-nothing, like the article import; imported FAQs start unpublished. */
    @Transactional
    public ImportResult importCsv(Long requestedCompanyId, MultipartFile file) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Row> rows = CsvImport.read(file, IMPORT_HEADER);
        List<Faq> ok = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (Row r : rows) {
            try {
                if (r.cells().size() != IMPORT_HEADER.size()) {
                    throw ApiException.badRequest(ESupportError.SUP_032, r.number());
                }
                Faq q = new Faq();
                q.setCompanyId(companyId);
                q.setTitle(Rows.required(r, 0, "title", 255));
                String answer = HtmlSanitizer.clean(Rows.required(r, 1, "description", 100_000));
                if (answer.isBlank()) {
                    throw ApiException.badRequest(ESupportError.SUP_022, r.number(), "description");
                }
                q.setDescription(answer);
                q.setPublished(false);
                ok.add(q);
            } catch (ApiException e) {
                errors.add(new RowError(r.number(), e.getErrorCode().getCode(),
                        messages.resolve(e.getErrorCode(), e.getArgs())));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportResult(rows.size(), 0, errors);
        }
        faqs.saveAll(ok);
        return new ImportResult(rows.size(), ok.size(), List.of());
    }

    private static void apply(Faq q, FaqForm f) {
        if (HtmlSanitizer.isBlank(f.description())) {
            throw ApiException.badRequest(ESupportError.SUP_034);
        }
        q.setTitle(f.title().trim());
        q.setDescription(HtmlSanitizer.clean(f.description()));
        q.setPublished(f.published());
    }

    private Faq require(Long id) {
        Faq q = faqs.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESupportError.SUP_016));
        TenantGuard.assertSameCompany(q.getCompanyId(), ESupportError.SUP_016);
        return q;
    }
}
