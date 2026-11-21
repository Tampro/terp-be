package com.terp.erp.employee.certificate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.certificate.LetterTemplateService.LetterTemplateForm;
import com.terp.erp.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LetterTemplateTest {
    private final LetterTemplateRepository repo = mock(LetterTemplateRepository.class);
    private final LetterTemplateService service = new LetterTemplateService(repo);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sanitizeKeepsFormattingAndAlignmentOnly() {
        String html = LetterPdf.sanitize("<p class=\"ql-align-center\" onclick=\"x()\">Hi <strong>{employee_name}</strong>"
                + "<script>alert(1)</script></p><p style=\"color:red; text-align: right\">Sign</p>"
                + "<a href=\"javascript:alert(1)\">x</a><img src=\"http://x/y.png\">");
        assertThat(html).contains("<p style=\"text-align: center\">Hi <strong>{employee_name}</strong></p>")
                .contains("<p style=\"text-align: right\">Sign</p>")
                .doesNotContain("script", "onclick", "color", "javascript", "img");
    }

    @Test
    void richTextIsRenderedWithValuesAsText() {
        byte[] pdf = LetterPdf.render("Công ty Demo", "Ngày {date}", "GIẤY XÁC NHẬN",
                "<h2>Tiêu đề</h2><p style=\"text-align: center\"><strong>{employee_name}</strong> <em>i</em> <u>u</u> "
                        + "<s>s</s><br>line</p><ul><li>một</li><li>hai</li></ul><ol><li>1</li></ol>"
                        + "<blockquote>quote</blockquote>loose text",
                Map.of("employee_name", "<b>Nguyễn Văn A</b>", "date", "01/12/2026"));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void defaultUntilSavedAndKoPrintsEnglish() {
        TestLogin.as(10L, "IAM_SETTINGS_MANAGE");
        when(repo.findByCompanyIdAndLetterTypeAndLang(10L, ELetterType.NOC, "vi")).thenReturn(Optional.empty());
        var d = service.get("noc", null, null);
        assertThat(d.customized()).isFalse();
        assertThat(d.content()).contains("{company_name}");
        assertThat(d.placeholders()).contains("{date}", "{employee_name}").doesNotContain("{salary}");
        assertThat(service.get("joining_letter", "ko", null).printedIn()).isEqualTo("en");

        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var s = service.save("NOC", "en", null, new LetterTemplateForm("<p>{employee_name}</p><script>x</script>"));
        assertThat(s.customized()).isTrue();
        assertThat(s.content()).isEqualTo("<p>{employee_name}</p>");
    }

    @Test
    void unknownLanguageAndTypeAreRefused() {
        TestLogin.as(10L, "IAM_SETTINGS_MANAGE");
        assertThatThrownBy(() -> service.get("NOC", "fr", null)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EEmployeeError.EMP_094);
        assertThatThrownBy(() -> service.get("OFFER", "en", null)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EEmployeeError.EMP_025);
    }
}
