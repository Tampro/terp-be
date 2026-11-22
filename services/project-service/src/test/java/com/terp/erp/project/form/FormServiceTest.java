package com.terp.erp.project.form;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.project.TestLogin;
import com.terp.erp.project.client.CrmClient;
import com.terp.erp.project.common.EFormError;
import com.terp.erp.project.form.FormConvertService.ConvertForm;
import com.terp.erp.project.form.FormField.EType;
import com.terp.erp.project.form.FormResponse.ETarget;
import com.terp.erp.project.form.FormService.SubmitForm;
import com.terp.erp.project.project.ProjectService;
import com.terp.erp.security.PermissionChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FormServiceTest {
    private static final String TOKEN = "tok-abcdefghijklmnopqrstuvwxyz0123456789ABCDE";
    private final ObjectMapper mapper = new ObjectMapper();
    private final FormRepository forms = mock(FormRepository.class);
    private final FormFieldRepository fields = mock(FormFieldRepository.class);
    private final FormResponseRepository responses = mock(FormResponseRepository.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final CrmClient crm = mock(CrmClient.class);
    private final PermissionChecker permissions = new PermissionChecker();
    private final FormService service = new FormService(forms, fields, responses, mapper);
    private final FormConvertService convert = new FormConvertService(service, responses, projects, crm, permissions,
            Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Form form(long id, long companyId, boolean active) {
        Form f = new Form();
        f.setId(id);
        f.setCompanyId(companyId);
        f.setName("Test contact form");
        f.setActive(active);
        f.setPublicToken(TOKEN);
        return f;
    }

    private static FormField field(String key, EType type, boolean required, String options) {
        FormField f = new FormField();
        f.setFieldKey(key);
        f.setLabel("Label " + key);
        f.setType(type);
        f.setRequired(required);
        f.setOptions(options);
        return f;
    }

    private void publicForm(boolean active) {
        when(forms.findByPublicTokenAndDeletedFalse(TOKEN)).thenReturn(Optional.of(form(3L, 10L, active)));
        when(fields.findByFormIdOrderByPositionAsc(3L)).thenReturn(List.of(
                field("email", EType.EMAIL, true, null),
                field("size", EType.SELECT, false, "[\"Small\",\"Large\"]"),
                field("secret", EType.PASSWORD, false, null)));
    }

    private static SubmitForm answers(String... kv) {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], TextNode.valueOf(kv[i + 1]));
        }
        return new SubmitForm(m);
    }

    private static void assertCode(Runnable r, EFormError code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void unknownTokenIs404() {
        when(forms.findByPublicTokenAndDeletedFalse("nope")).thenReturn(Optional.empty());
        assertCode(() -> service.publicForm("nope"), EFormError.FRM_002);
    }

    @Test
    void inactiveFormAnswersTheSame404() {
        publicForm(false);
        assertCode(() -> service.publicForm(TOKEN), EFormError.FRM_002);
        assertCode(() -> service.submit(TOKEN, answers("email", "visitor@example.com")), EFormError.FRM_002);
        verify(responses, never()).save(any());
    }

    @Test
    void publicDefinitionHidesCompanyAndIds() {
        publicForm(true);
        FormService.PublicFormDto dto = service.publicForm(TOKEN);
        assertThat(dto.name()).isEqualTo("Test contact form");
        assertThat(dto.fields()).extracting(FormService.PublicField::key).containsExactly("email", "size", "secret");
        assertThat(dto.fields().get(1).options()).containsExactly("Small", "Large");
    }

    @Test
    void requiredFieldIsChecked() {
        publicForm(true);
        assertCode(() -> service.submit(TOKEN, answers("size", "Small")), EFormError.FRM_004);
    }

    @Test
    void emailFormatIsChecked() {
        publicForm(true);
        assertCode(() -> service.submit(TOKEN, answers("email", "not-an-email")), EFormError.FRM_005);
    }

    @Test
    void optionMustBeOneOfTheOptions() {
        publicForm(true);
        assertCode(() -> service.submit(TOKEN, answers("email", "visitor@example.com", "size", "Huge")),
                EFormError.FRM_011);
    }

    @Test
    void unknownAnswerKeyIsRefused() {
        publicForm(true);
        assertCode(() -> service.submit(TOKEN, answers("email", "visitor@example.com", "other", "x")),
                EFormError.FRM_013);
    }

    @Test
    void responseTakesFormCompanyAndHashesPassword() {
        publicForm(true);
        service.submit(TOKEN, answers("email", "visitor@example.com", "size", "Large", "secret", "Test-Only-1"));
        ArgumentCaptor<FormResponse> saved = ArgumentCaptor.forClass(FormResponse.class);
        verify(responses).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(10L);
        assertThat(saved.getValue().getAnswers()).doesNotContain("Test-Only-1").contains("sha256$");
    }

    @Test
    void anotherCompanysFormIs404() {
        TestLogin.as(20L, "FORM_VIEW");
        when(forms.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(form(3L, 10L, true)));
        assertCode(() -> service.get(3L), EFormError.FRM_001);
    }

    @Test
    void selectWithoutOptionsIsRefused() {
        TestLogin.as(10L, "FORM_MANAGE");
        FormService.FormForm f = new FormService.FormForm(null, "Test form", true, null,
                List.of(new FormService.FieldForm(null, "Size", EType.SELECT, false, null, List.of())));
        assertCode(() -> service.create(f), EFormError.FRM_003);
    }

    private FormResponse storedResponse(String targetType) throws Exception {
        when(forms.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(form(3L, 10L, true)));
        FormResponse r = new FormResponse();
        r.setId(7L);
        r.setFormId(3L);
        r.setCompanyId(10L);
        r.setAnswers(mapper.writeValueAsString(List.of(
                new FormAnswerValidator.Answer("full_name", "Name", EType.TEXT, "Test Visitor"),
                new FormAnswerValidator.Answer("email", "Email", EType.EMAIL, "visitor@example.com"),
                new FormAnswerValidator.Answer("message", "Message", EType.TEXTAREA, "Wants a demo"))));
        if (targetType != null) {
            r.setConvertedTargetType(ETarget.valueOf(targetType));
            r.setConvertedTargetId("44");
        }
        when(responses.lock(7L)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void convertToLeadSendsMappedValues() throws Exception {
        TestLogin.as(10L, "FORM_MANAGE");
        FormResponse r = storedResponse(null);
        when(crm.createLead(anyMap())).thenReturn("55");
        FormConvertService.ConvertResult res = convert.convert(3L, 7L, new ConvertForm(ETarget.LEAD,
                Map.of("name", "full_name", "email", "email", "subject", "message"),
                Map.of("pipelineId", "1", "ownerUserId", "12")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(crm).createLead(body.capture());
        assertThat(body.getValue()).containsEntry("pipelineId", 1L).containsEntry("name", "Test Visitor")
                .containsEntry("email", "visitor@example.com").containsEntry("subject", "Wants a demo")
                .containsEntry("ownerUserId", 12L).doesNotContainKey("stageId");
        assertThat(res.targetId()).isEqualTo("55");
        assertThat(r.getConvertedTargetType()).isEqualTo(ETarget.LEAD);
        assertThat(r.getConvertedTargetId()).isEqualTo("55");
    }

    @Test
    void convertTwiceIsRefused() throws Exception {
        TestLogin.as(10L, "FORM_MANAGE");
        storedResponse("LEAD");
        assertCode(() -> convert.convert(3L, 7L, new ConvertForm(ETarget.LEAD, Map.of(), Map.of())),
                EFormError.FRM_015);
        verifyNoInteractions(crm);
    }

    @Test
    void convertMissingRequiredTargetFieldIsRefused() throws Exception {
        TestLogin.as(10L, "FORM_MANAGE");
        storedResponse(null);
        assertCode(() -> convert.convert(3L, 7L, new ConvertForm(ETarget.LEAD,
                Map.of("name", "full_name", "email", "email", "subject", "message"), Map.of("pipelineId", "1"))),
                EFormError.FRM_017);
        verifyNoInteractions(crm);
    }

    @Test
    void convertToProjectNeedsProjectManage() throws Exception {
        TestLogin.as(10L, "FORM_MANAGE");
        storedResponse(null);
        assertThatThrownBy(() -> convert.convert(3L, 7L, new ConvertForm(ETarget.PROJECT,
                Map.of("name", "full_name"), Map.of())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommonError.COMMON_004);
        verifyNoInteractions(projects);
    }

    @Test
    void convertOfAnotherCompanysResponseIs404() throws Exception {
        TestLogin.as(20L, "FORM_MANAGE");
        storedResponse(null);
        assertCode(() -> convert.convert(3L, 7L, new ConvertForm(ETarget.LEAD, Map.of(), Map.of())),
                EFormError.FRM_001);
    }
}
