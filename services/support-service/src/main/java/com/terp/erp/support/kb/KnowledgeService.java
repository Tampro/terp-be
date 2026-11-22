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
import jakarta.validation.constraints.NotNull;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Knowledge base categories and articles (reference KnowledgeController, KnowledgebaseCategoryController). */
@Service
@RequiredArgsConstructor
public class KnowledgeService {
    /** CSV header of the article import; category = title of an existing category of the company. */
    public static final List<String> IMPORT_HEADER = List.of("title", "description", "category", "content");

    public record CategoryDto(String id, String title) {
        static CategoryDto of(KbCategory c) {
            return new CategoryDto(c.getId().toString(), c.getTitle());
        }
    }

    public record CategoryForm(
            @Schema(example = "Getting started", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String title) {
    }

    public record ArticleRow(String id, String categoryId, String categoryTitle, String title, String description,
                             boolean published, Instant createdDate, Instant updatedDate) {
    }

    public record ArticleDto(ArticleRow article, String content) {
    }

    public record ArticleForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Schema(example = "How to reset your password", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String title,
            @Schema(description = "Short summary, plain text", example = "Steps to set a new password",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 500) String description,
            @Schema(description = "HTML; sanitized", example = "<p>Open Settings, then Security.</p>")
            @Size(max = 200_000) String content,
            @Schema(description = "Shown on the public portal") boolean published) {
    }

    private final KbCategoryRepository categories;
    private final KbArticleRepository articles;
    private final RestExceptionHandler messages;

    // ---- categories ----

    @Transactional(readOnly = true)
    public List<CategoryDto> categories(Long requestedCompanyId) {
        return categoriesOf(Texts.company(requestedCompanyId));
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categoriesOf(Long companyId) {
        return categories.findByCompanyIdAndDeletedFalseOrderByTitleAsc(companyId).stream().map(CategoryDto::of)
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(Long requestedCompanyId, CategoryForm f) {
        KbCategory c = new KbCategory();
        c.setCompanyId(Texts.company(requestedCompanyId));
        c.setTitle(f.title().trim());
        return CategoryDto.of(categories.save(c));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryForm f) {
        KbCategory c = requireCategory(id);
        c.setTitle(f.title().trim());
        return CategoryDto.of(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        KbCategory c = requireCategory(id);
        if (articles.existsByCategoryIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(ESupportError.SUP_017);
        }
        c.setDeleted(true);
    }

    // ---- articles ----

    /** published null = all (admin); the portal asks for published only. */
    @Transactional(readOnly = true)
    public Page<ArticleRow> list(Long requestedCompanyId, Boolean published, Long categoryId, String keyword,
                                 Pageable pageable) {
        return listOf(Texts.company(requestedCompanyId), published, categoryId, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ArticleRow> listOf(Long companyId, Boolean published, Long categoryId, String keyword,
                                   Pageable pageable) {
        Page<KbArticle> page = articles.search(companyId, published, categoryId, Texts.kw(keyword), pageable);
        Map<Long, String> titles = titles(companyId);
        return page.map(a -> row(a, titles));
    }

    @Transactional(readOnly = true)
    public ArticleDto get(Long id) {
        return dto(require(id));
    }

    /** For the portal: a published article of the company, otherwise SUP_015 (404). */
    @Transactional(readOnly = true)
    public ArticleDto getPublished(Long companyId, Long id) {
        KbArticle a = articles.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), companyId) && x.isPublished())
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_015));
        return dto(a);
    }

    @Transactional
    public ArticleDto create(Long requestedCompanyId, ArticleForm f) {
        KbArticle a = new KbArticle();
        a.setCompanyId(Texts.company(requestedCompanyId));
        apply(a, f);
        return dto(articles.save(a));
    }

    @Transactional
    public ArticleDto update(Long id, ArticleForm f) {
        KbArticle a = require(id);
        apply(a, f);
        return dto(a);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** All-or-nothing: every row is checked first; nothing is saved when one row is refused. Rows start unpublished. */
    @Transactional
    public ImportResult importCsv(Long requestedCompanyId, MultipartFile file) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Row> rows = CsvImport.read(file, IMPORT_HEADER);
        Map<String, KbCategory> byTitle = categories.findByCompanyIdAndDeletedFalseOrderByTitleAsc(companyId).stream()
                .collect(Collectors.toMap(c -> c.getTitle().toLowerCase(Locale.ROOT), Function.identity(),
                        (x, y) -> x));
        List<KbArticle> ok = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (Row r : rows) {
            try {
                if (r.cells().size() != IMPORT_HEADER.size()) {
                    throw ApiException.badRequest(ESupportError.SUP_032, r.number());
                }
                String title = Rows.required(r, 0, "title", 255);
                String description = Rows.required(r, 1, "description", 500);
                String categoryTitle = Rows.required(r, 2, "category", 255);
                KbCategory c = byTitle.get(categoryTitle.toLowerCase(Locale.ROOT));
                if (c == null) {
                    throw ApiException.badRequest(ESupportError.SUP_024, r.number(), categoryTitle);
                }
                String content = Rows.optional(r, 3, "content", 200_000);
                KbArticle a = new KbArticle();
                a.setCompanyId(companyId);
                a.setCategoryId(c.getId());
                a.setTitle(title);
                a.setDescription(description);
                a.setContent(content == null ? null : HtmlSanitizer.clean(content));
                a.setPublished(false);
                ok.add(a);
            } catch (ApiException e) {
                errors.add(new RowError(r.number(), e.getErrorCode().getCode(),
                        messages.resolve(e.getErrorCode(), e.getArgs())));
            }
        }
        if (!errors.isEmpty()) {
            return new ImportResult(rows.size(), 0, errors);
        }
        articles.saveAll(ok);
        return new ImportResult(rows.size(), ok.size(), List.of());
    }

    // ----

    private void apply(KbArticle a, ArticleForm f) {
        KbCategory c = categories.findByIdAndDeletedFalse(f.categoryId())
                .filter(x -> Objects.equals(x.getCompanyId(), a.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(ESupportError.SUP_014));
        a.setCategoryId(c.getId());
        a.setTitle(f.title().trim());
        a.setDescription(f.description().trim());
        String content = Texts.blankToNull(f.content());
        a.setContent(content == null ? null : HtmlSanitizer.clean(content));
        a.setPublished(f.published());
    }

    private KbCategory requireCategory(Long id) {
        KbCategory c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_014));
        TenantGuard.assertSameCompany(c.getCompanyId(), ESupportError.SUP_014);
        return c;
    }

    private KbArticle require(Long id) {
        KbArticle a = articles.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_015));
        TenantGuard.assertSameCompany(a.getCompanyId(), ESupportError.SUP_015);
        return a;
    }

    private Map<Long, String> titles(Long companyId) {
        return categories.findByCompanyIdAndDeletedFalseOrderByTitleAsc(companyId).stream()
                .collect(Collectors.toMap(KbCategory::getId, KbCategory::getTitle));
    }

    private ArticleDto dto(KbArticle a) {
        return new ArticleDto(row(a, titles(a.getCompanyId())), a.getContent());
    }

    private static ArticleRow row(KbArticle a, Map<Long, String> titles) {
        return new ArticleRow(String.valueOf(a.getId()), a.getCategoryId().toString(), titles.get(a.getCategoryId()),
                a.getTitle(), a.getDescription(), a.isPublished(), a.getCreatedDate(), a.getUpdatedDate());
    }
}
