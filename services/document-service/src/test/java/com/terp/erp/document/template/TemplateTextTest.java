package com.terp.erp.document.template;

import org.junit.jupiter.api.Test;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateTextTest {
    private static final String CONTENT = """
            INTERNSHIP AGREEMENT
            This agreement is made between {{company_name}} and {{ intern_name }}.
            1. Term: from {{start_date}} to {{end_date}}.
            2. Stipend: {{stipend_amount}} per month.
            Điều 3 Confidentiality applies to {{intern_name}}.
            """;

    @Test
    void variablesAreDeclaredPlusFoundInOrder() {
        assertThat(TemplateText.variables("company_name, mentor_name", CONTENT)).containsExactly("company_name",
                "mentor_name", "intern_name", "start_date", "end_date", "stipend_amount");
        assertThatThrownBy(() -> TemplateText.variables("good, bad name, 9x", CONTENT))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EDocumentError.DOC_022))
                .satisfies(e -> assertThat(((ApiException) e).getArgs()).containsExactly("bad name, 9x"));
    }

    @Test
    void clausesAndFill() {
        assertThat(TemplateText.clauses(CONTENT)).isEqualTo(3);
        String filled = TemplateText.fill(CONTENT, Map.of("company_name", "Test Co", "intern_name", "Nguyễn Thử",
                "start_date", "2026-12-01", "end_date", "2027-02-28", "stipend_amount", "$5 \\ 1"), false);
        assertThat(filled).contains("between Test Co and Nguyễn Thử.").contains("$5 \\ 1 per month")
                .doesNotContain("{{");
        assertThatThrownBy(() -> TemplateText.fill(CONTENT, Map.of("company_name", "X"), false))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EDocumentError.DOC_021))
                .satisfies(e -> assertThat(((ApiException) e).getArgs()[0])
                        .isEqualTo("intern_name, start_date, end_date, stipend_amount"));
        assertThat(TemplateText.fill(CONTENT, Map.of(), true)).contains("[intern_name]");
    }

    @Test
    void pdfAndFileName() {
        byte[] pdf = TemplatePdf.render("Hợp đồng thực tập", "Dòng một\n\nDòng ba");
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(DocTemplateService.slug("Hợp đồng Thực tập 2026!")).isEqualTo("hop-dong-thuc-tap-2026");
        assertThat(DocTemplateService.slug("***")).isEqualTo("document");
    }
}
