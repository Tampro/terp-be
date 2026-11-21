package com.terp.erp.recruitment.offertemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.offertemplate.OfferTemplateService.TemplateForm;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferTemplateServiceTest {
    private final OfferTemplateRepository templates = mock(OfferTemplateRepository.class);
    private final OfferTemplateService service = new OfferTemplateService(templates);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void variablesAreParsedAndChecked() {
        assertThat(OfferTemplateService.parseVariables(" candidate_name, position,,candidate_name ,start_date"))
                .containsExactly("candidate_name", "position", "start_date");
        assertThat(OfferTemplateService.parseVariables(null)).isEmpty();
        assertCode(() -> OfferTemplateService.parseVariables("ok, Bad-Name"), ERecruitmentError.REC_033);
    }

    @Test
    void createSanitizesContent() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        when(templates.save(any())).thenAnswer(i -> {
            OfferTemplate t = i.getArgument(0);
            t.setId(1L);
            return t;
        });
        var d = service.create(null, new TemplateForm(" Standard ",
                "<p onclick=\"x()\">Dear {{candidate_name}}</p><script>bad()</script>", "candidate_name", null));
        assertThat(d.name()).isEqualTo("Standard");
        assertThat(d.content()).isEqualTo("<p>Dear {{candidate_name}}</p>");
        assertThat(d.contentPreview()).isEqualTo("Dear {{candidate_name}}");
        assertThat(d.variables()).containsExactly("candidate_name");
    }

    @Test
    void fillEscapesValuesAndKeepsUndeclared() {
        String out = OfferTemplateService.fill("<p>Hi {{ candidate_name }}, {{position}} at {{other}}</p>",
                Set.of("candidate_name", "position"),
                Map.of("candidate_name", " <b>Test</b> ", "position", "Dev & QA"));
        assertThat(out).isEqualTo("<p>Hi &lt;b&gt;Test&lt;/b&gt;, Dev &amp; QA at {{other}}</p>");
    }

    @Test
    void generateNeedsEveryVariable() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        OfferTemplate t = new OfferTemplate();
        t.setId(1L);
        t.setCompanyId(10L);
        t.setName("Standard");
        t.setContent("<p>{{candidate_name}} - {{salary}}</p>");
        t.getVariables().addAll(java.util.List.of("candidate_name", "salary"));
        when(templates.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.generate(1L, Map.of("candidate_name", "Test", "salary", " ")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getArgs()).containsExactly("salary"));
        assertThat(service.generate(1L, Map.of("candidate_name", "Test", "salary", "1000")).content())
                .isEqualTo("<p>Test - 1000</p>");
        t.setCompanyId(11L);
        assertCode(() -> service.generate(1L, Map.of()), ERecruitmentError.REC_032);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
