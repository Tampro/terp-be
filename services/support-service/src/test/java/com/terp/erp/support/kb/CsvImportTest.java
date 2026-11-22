package com.terp.erp.support.kb;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.exception.RestExceptionHandler;
import com.terp.erp.support.TestLogin;
import com.terp.erp.support.common.ESupportError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CsvImportTest {
    private final KbCategoryRepository categories = mock(KbCategoryRepository.class);
    private final KbArticleRepository articles = mock(KbArticleRepository.class);
    private final FaqRepository faqRepo = mock(FaqRepository.class);
    private final RestExceptionHandler messages = mock(RestExceptionHandler.class);
    private final KnowledgeService kb = new KnowledgeService(categories, articles, messages);
    private final FaqService faqs = new FaqService(faqRepo, messages);

    CsvImportTest() {
        when(messages.resolve(any(ErrorCode.class), any(Object[].class)))
                .thenAnswer(i -> ((ErrorCode) i.getArgument(0)).getCode());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MockMultipartFile csv(String name, String text) {
        return new MockMultipartFile("file", name, "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCode(Runnable call, ESupportError code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void parsesQuotesEscapedQuotesLineBreaksAndBom() {
        var rows = CsvImport.parse("﻿Title,Description\r\n\"A, b\",\"say \"\"hi\"\"\nnext line\"\r\n\r\nc,d",
                FaqService.IMPORT_HEADER);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).cells()).containsExactly("A, b", "say \"hi\"\nnext line");
        assertThat(rows.get(0).number()).isEqualTo(2);
        assertThat(rows.get(1).number()).isEqualTo(5);
    }

    @Test
    void fileChecksComeBeforeAnyRow() {
        TestLogin.as(10L, "SUPPORT_KB_MANAGE");
        assertCode(() -> faqs.importCsv(null, csv("faq.txt", "title,description\na,b")), ESupportError.SUP_018);
        assertCode(() -> faqs.importCsv(null, csv("faq.csv", "")), ESupportError.SUP_018);
        assertCode(() -> faqs.importCsv(null, csv("faq.csv", "question,answer\na,b")), ESupportError.SUP_021);
        assertCode(() -> faqs.importCsv(null, csv("faq.csv", "title,description\n\"a,b")), ESupportError.SUP_033);
        StringBuilder big = new StringBuilder("title,description\n");
        for (int i = 0; i <= CsvImport.MAX_ROWS; i++) {
            big.append("q").append(i).append(",a\n");
        }
        assertCode(() -> faqs.importCsv(null, csv("faq.csv", big.toString())), ESupportError.SUP_020);
        byte[] huge = new byte[CsvImport.MAX_BYTES + 1];
        assertCode(() -> faqs.importCsv(null, new MockMultipartFile("file", "faq.csv", "text/csv", huge)),
                ESupportError.SUP_019);
        verify(faqRepo, never()).saveAll(anyList());
    }

    @Test
    void oneBadRowMeansNothingIsSavedAndEveryErrorIsReported() {
        TestLogin.as(10L, "SUPPORT_KB_MANAGE");
        var result = faqs.importCsv(null, csv("faq.csv",
                "title,description\nGood question,Good answer\n,missing title\nextra,col,umn\n"));

        assertThat(result.rows()).isEqualTo(3);
        assertThat(result.created()).isZero();
        assertThat(result.errors()).extracting(CsvImport.RowError::row).containsExactly(3, 4);
        assertThat(result.errors()).extracting(CsvImport.RowError::code).containsExactly("SUP_022", "SUP_032");
        verify(faqRepo, never()).saveAll(anyList());
    }

    @Test
    void aCleanFileIsSavedUnpublishedForTheCallersCompany() {
        TestLogin.as(10L, "SUPPORT_KB_MANAGE");
        var result = faqs.importCsv(null, csv("faq.csv", "title,description\nQ1,<p>A1</p>\nQ2,A2\n"));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        verify(faqRepo).saveAll(org.mockito.ArgumentMatchers.<List<Faq>>argThat(l -> l.size() == 2
                && l.stream().allMatch(f -> f.getCompanyId() == 10L && !f.isPublished())));
    }

    @Test
    void articleImportNeedsAnExistingCategoryOfTheCompany() {
        TestLogin.as(10L, "SUPPORT_KB_MANAGE");
        KbCategory c = new KbCategory();
        c.setId(4L);
        c.setCompanyId(10L);
        c.setTitle("Billing");
        when(categories.findByCompanyIdAndDeletedFalseOrderByTitleAsc(10L)).thenReturn(List.of(c));

        var result = kb.importCsv(null, csv("kb.csv",
                "title,description,category,content\nT1,D1,billing,<p>x</p>\nT2,D2,Unknown,\n"));

        assertThat(result.created()).isZero();
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(3);
            assertThat(e.code()).isEqualTo("SUP_024");
        });
        verify(articles, never()).saveAll(anyList());
    }
}
