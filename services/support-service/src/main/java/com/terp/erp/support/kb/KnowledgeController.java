package com.terp.erp.support.kb;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.kb.CsvImport.ImportResult;
import com.terp.erp.support.kb.FaqService.FaqDto;
import com.terp.erp.support.kb.FaqService.FaqForm;
import com.terp.erp.support.kb.KnowledgeService.ArticleDto;
import com.terp.erp.support.kb.KnowledgeService.ArticleForm;
import com.terp.erp.support.kb.KnowledgeService.ArticleRow;
import com.terp.erp.support.kb.KnowledgeService.CategoryDto;
import com.terp.erp.support.kb.KnowledgeService.CategoryForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
@Tag(name = "Knowledge base & FAQ", description = "Knowledge base categories and articles, FAQs, CSV import (ERP manual 18)")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeController {
    private static final String IMPORT_RESULT = "200 even when rows are refused: errors lists each refused row "
            + "(row = line in the file, header = 1) and then nothing is saved (created = 0).";

    private final KnowledgeService kb;
    private final FaqService faqs;

    @GetMapping("/kb-categories")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_VIEW')")
    @Operation(summary = "Knowledge base categories, by title")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<List<CategoryDto>> categories(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(kb.categories(companyId));
    }

    @PostMapping("/kb-categories")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Create a knowledge base category")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001")})
    public ApiMessageDto<CategoryDto> createCategory(@RequestParam(required = false) Long companyId,
                                                     @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(kb.createCategory(companyId, form));
    }

    @PutMapping("/kb-categories/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Replace a knowledge base category (full replacement: title)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001"),
            @ApiResponse(responseCode = "404", description = "SUP_014")})
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(kb.updateCategory(id, form));
    }

    @DeleteMapping("/kb-categories/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Delete a knowledge base category (soft); refused while it holds articles")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_017"),
            @ApiResponse(responseCode = "404", description = "SUP_014")})
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        kb.deleteCategory(id);
        return ApiMessageDto.ok(null);
    }

    @GetMapping("/kb")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_VIEW')")
    @Operation(summary = "List knowledge base articles", description = "published null = all; keyword matches title "
            + "and description.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<ResponseListDto<ArticleRow>> list(
            @RequestParam(required = false) Long companyId, @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(kb.list(companyId, published, categoryId, keyword, pageable)));
    }

    @GetMapping("/kb/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_VIEW')")
    @Operation(summary = "A knowledge base article with its content")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_015")})
    public ApiMessageDto<ArticleDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(kb.get(id));
    }

    @PostMapping("/kb")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Create a knowledge base article")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_014 (category)")})
    public ApiMessageDto<ArticleDto> create(@RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody ArticleForm form) {
        return ApiMessageDto.ok(kb.create(companyId, form));
    }

    @PutMapping("/kb/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Replace a knowledge base article (full replacement)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_014"),
            @ApiResponse(responseCode = "404", description = "SUP_015")})
    public ApiMessageDto<ArticleDto> update(@PathVariable Long id, @Valid @RequestBody ArticleForm form) {
        return ApiMessageDto.ok(kb.update(id, form));
    }

    @DeleteMapping("/kb/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Delete a knowledge base article (soft)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_015")})
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        kb.delete(id);
        return ApiMessageDto.ok(null);
    }

    @PostMapping(value = "/kb/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Import articles from CSV (all-or-nothing)",
            description = "UTF-8 .csv, at most 1 MB and 500 rows. Header exactly: title,description,category,content. "
                    + "category = title of an existing category. Imported articles are unpublished. " + IMPORT_RESULT)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK (see errors)"),
            @ApiResponse(responseCode = "400", description = "SUP_001, SUP_018, SUP_019, SUP_020, SUP_021, SUP_033; "
                    + "row errors: SUP_022, SUP_023, SUP_024, SUP_032")})
    public ApiMessageDto<ImportResult> importCsv(@RequestParam(required = false) Long companyId,
                                                 @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(kb.importCsv(companyId, file));
    }

    @GetMapping("/faqs")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_VIEW')")
    @Operation(summary = "List FAQs", description = "published null = all; keyword matches the question.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<ResponseListDto<FaqDto>> faqs(
            @RequestParam(required = false) Long companyId, @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String keyword, @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(faqs.list(companyId, published, keyword, pageable)));
    }

    @GetMapping("/faqs/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_VIEW')")
    @Operation(summary = "An FAQ")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_016")})
    public ApiMessageDto<FaqDto> faq(@PathVariable Long id) {
        return ApiMessageDto.ok(faqs.get(id));
    }

    @PostMapping("/faqs")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Create an FAQ")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_034")})
    public ApiMessageDto<FaqDto> createFaq(@RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody FaqForm form) {
        return ApiMessageDto.ok(faqs.create(companyId, form));
    }

    @PutMapping("/faqs/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Replace an FAQ (full replacement)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_034"),
            @ApiResponse(responseCode = "404", description = "SUP_016")})
    public ApiMessageDto<FaqDto> updateFaq(@PathVariable Long id, @Valid @RequestBody FaqForm form) {
        return ApiMessageDto.ok(faqs.update(id, form));
    }

    @DeleteMapping("/faqs/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Delete an FAQ (soft)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_016")})
    public ApiMessageDto<Void> deleteFaq(@PathVariable Long id) {
        faqs.delete(id);
        return ApiMessageDto.ok(null);
    }

    @PostMapping(value = "/faqs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@PermissionChecker.has('SUPPORT_KB_MANAGE')")
    @Operation(summary = "Import FAQs from CSV (all-or-nothing)",
            description = "UTF-8 .csv, at most 1 MB and 500 rows. Header exactly: title,description. Imported FAQs are "
                    + "unpublished. " + IMPORT_RESULT)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK (see errors)"),
            @ApiResponse(responseCode = "400", description = "SUP_001, SUP_018, SUP_019, SUP_020, SUP_021, SUP_033; "
                    + "row errors: SUP_022, SUP_023, SUP_032")})
    public ApiMessageDto<ImportResult> importFaqs(@RequestParam(required = false) Long companyId,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(faqs.importCsv(companyId, file));
    }
}
