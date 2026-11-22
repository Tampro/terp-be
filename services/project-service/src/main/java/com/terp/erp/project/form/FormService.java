package com.terp.erp.project.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EFormError;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.form.FormAnswerValidator.Answer;
import com.terp.erp.project.form.FormField.EType;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Form builder (ERP manual 11.2, FR-COL-FRM-001): forms, the public link and its responses. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper PLAIN = new ObjectMapper();
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<Answer>> ANSWERS = new TypeReference<>() {
    };
    static final String PASSWORD_MASK = "******";

    public record FieldForm(
            @Schema(description = "Stable field key; send the existing key back on edit to keep answers and mappings"
                    + " attached; omit = generated", example = "email") @Size(max = 40) String key,
            @Schema(description = "Label", example = "Your email", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String label,
            @Schema(description = "TEXT, EMAIL, NUMBER, PHONE, URL, PASSWORD, TEXTAREA, SELECT, RADIO, CHECKBOX, DATE,"
                    + " TIME", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EType type,
            @Schema(description = "Answer required", example = "true") boolean required,
            @Schema(description = "Placeholder", example = "name@example.com") @Size(max = 255) String placeholder,
            @Schema(description = "Options (SELECT, RADIO, CHECKBOX only; at least one)",
                    example = "[\"Small\", \"Large\"]")
            List<@NotBlank @Size(max = 255) String> options) {
    }

    public record FormForm(
            @Schema(description = "Super admin only: the company to act on (create)", example = "10") Long companyId,
            @Schema(description = "Form name", example = "Test contact form",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255) String name,
            @Schema(description = "Accepts responses; omit = true", example = "true") Boolean active,
            @Schema(description = "SINGLE_COLUMN, TWO_COLUMN or CARD; omit = SINGLE_COLUMN", example = "TWO_COLUMN")
            Form.ELayout layout,
            @Schema(description = "Fields in display order (replaces the current ones)",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid List<@NotNull FieldForm> fields) {
    }

    public record FieldDto(String id, String key, String label, EType type, boolean required, String placeholder,
                           List<String> options) {
    }

    public record FormRow(String id, String companyId, String name, boolean active, Form.ELayout layout,
                          String publicToken, Instant createdDate) {
    }

    public record FormDto(FormRow form, List<FieldDto> fields) {
    }

    public record PublicField(String key, String label, EType type, boolean required, String placeholder,
                              List<String> options) {
    }

    /** What the public link shows: no company, no internal ids. */
    public record PublicFormDto(String name, Form.ELayout layout, List<PublicField> fields) {
    }

    public record SubmitForm(
            @Schema(description = "Answers keyed by field key: a string, or an array of strings for CHECKBOX",
                    example = "{\"email\": \"visitor@example.com\", \"size\": \"Large\"}",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Map<String, JsonNode> answers) {
    }

    public record AnswerDto(String key, String label, EType type, Object value) {
    }

    public record ResponseDto(String id, String formId, List<AnswerDto> answers,
                              FormResponse.ETarget convertedTargetType, String convertedTargetId, Instant convertedAt,
                              Instant createdDate) {
    }

    private final FormRepository forms;
    private final FormFieldRepository fields;
    private final FormResponseRepository responses;
    private final ObjectMapper mapper;

    // ---------- forms ----------

    @Transactional(readOnly = true)
    public Page<FormRow> list(Long companyId, String keyword, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EProjectError.PRJ_001);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return forms.search(company, kw, pageable).map(FormService::row);
    }

    @Transactional(readOnly = true)
    public FormDto get(Long id) {
        Form f = require(id);
        return dto(f, fields.findByFormIdOrderByPositionAsc(f.getId()));
    }

    @Transactional
    public FormDto create(FormForm form) {
        Long company = TenantGuard.resolveCompany(form.companyId(), EProjectError.PRJ_001);
        List<FormField> built = buildFields(form.fields());
        Form f = new Form();
        f.setCompanyId(company);
        f.setPublicToken(newToken());
        apply(f, form);
        f = forms.save(f);
        return dto(f, saveFields(f.getId(), built));
    }

    /** Full replacement, fields included; a field keeps its answers and mappings only when its key is sent back. */
    @Transactional
    public FormDto update(Long id, FormForm form) {
        Form f = require(id);
        List<FormField> built = buildFields(form.fields());
        apply(f, form);
        forms.save(f);
        fields.deleteAllOfForm(f.getId());
        return dto(f, saveFields(f.getId(), built));
    }

    @Transactional
    public void delete(Long id) {
        Form f = require(id);
        f.setDeleted(true);
        forms.save(f);
    }

    /** The old link stops working at once. */
    @Transactional
    public FormRow regenerateToken(Long id) {
        Form f = require(id);
        f.setPublicToken(newToken());
        return row(forms.save(f));
    }

    // ---------- public link ----------

    @Transactional(readOnly = true)
    public PublicFormDto publicForm(String token) {
        Form f = requireActiveByToken(token);
        List<PublicField> out = fields.findByFormIdOrderByPositionAsc(f.getId()).stream()
                .map(x -> new PublicField(x.getFieldKey(), x.getLabel(), x.getType(), x.isRequired(),
                        x.getPlaceholder(), x.getType().hasOptions() ? readOptions(x.getOptions()) : null))
                .toList();
        return new PublicFormDto(f.getName(), f.getLayout(), out);
    }

    @Transactional
    public void submit(String token, SubmitForm form) {
        Form f = requireActiveByToken(token);
        List<Answer> answers = FormAnswerValidator.validate(fields.findByFormIdOrderByPositionAsc(f.getId()),
                form.answers());
        FormResponse r = new FormResponse();
        r.setCompanyId(f.getCompanyId());
        r.setFormId(f.getId());
        r.setAnswers(write(answers));
        responses.save(r);
        // Ids only: answers may carry personal data.
        log.info("form {} received a response", f.getId());
    }

    // ---------- responses ----------

    @Transactional(readOnly = true)
    public Page<ResponseDto> responses(Long formId, LocalDate from, LocalDate to, Pageable pageable) {
        Form f = require(formId);
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest(EFormError.FRM_020);
        }
        // TODO(assumption): from/to are UTC days; the company time zone is not known in this service yet.
        Instant start = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return responses.search(f.getId(), start, end, pageable).map(this::responseDto);
    }

    @Transactional(readOnly = true)
    public ResponseDto response(Long formId, Long responseId) {
        return responseDto(requireResponse(formId, responseId, false));
    }

    @Transactional
    public void deleteResponse(Long formId, Long responseId) {
        FormResponse r = requireResponse(formId, responseId, false);
        r.setDeleted(true);
        responses.save(r);
    }

    // ---------- shared with FormConvertService ----------

    Form require(Long id) {
        Form f = forms.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EFormError.FRM_001));
        TenantGuard.assertSameCompany(f.getCompanyId(), EFormError.FRM_001);
        return f;
    }

    FormResponse requireResponse(Long formId, Long responseId, boolean lock) {
        Form f = require(formId);
        FormResponse r = (lock ? responses.lock(responseId) : responses.findByIdAndDeletedFalse(responseId))
                .orElseThrow(() -> ApiException.notFound(EFormError.FRM_014));
        if (!r.getFormId().equals(f.getId())) {
            throw ApiException.notFound(EFormError.FRM_014);
        }
        TenantGuard.assertSameCompany(r.getCompanyId(), EFormError.FRM_014);
        return r;
    }

    List<Answer> readAnswers(String json) {
        try {
            return mapper.readValue(json, ANSWERS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("form_response.answers is not valid JSON", e);
        }
    }

    static List<String> readOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return PLAIN.readValue(json, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("form_field.options is not valid JSON", e);
        }
    }

    // ---------- helpers ----------

    /** Unknown, deleted and inactive answer the same 404: the caller learns nothing about which links exist. */
    private Form requireActiveByToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            throw ApiException.notFound(EFormError.FRM_002);
        }
        return forms.findByPublicTokenAndDeletedFalse(token).filter(Form::isActive)
                .orElseThrow(() -> ApiException.notFound(EFormError.FRM_002));
    }

    private static void apply(Form f, FormForm form) {
        f.setName(form.name().trim());
        f.setActive(form.active() == null || form.active());
        f.setLayout(form.layout() == null ? Form.ELayout.SINGLE_COLUMN : form.layout());
    }

    private List<FormField> buildFields(List<FieldForm> in) {
        if (in.size() > FormAnswerValidator.MAX_FIELDS) {
            throw ApiException.badRequest(EFormError.FRM_021, FormAnswerValidator.MAX_FIELDS,
                    FormAnswerValidator.MAX_OPTIONS);
        }
        Set<String> seen = new HashSet<>();
        List<FormField> out = new ArrayList<>(in.size());
        int pos = 0;
        for (FieldForm x : in) {
            String key = x.key() == null || x.key().isBlank() ? newKey() : x.key().trim();
            if (!KEY.matcher(key).matches()) {
                throw ApiException.badRequest(EFormError.FRM_018, "key");
            }
            if (!seen.add(key)) {
                throw ApiException.badRequest(EFormError.FRM_022, key);
            }
            FormField f = new FormField();
            f.setFieldKey(key);
            f.setLabel(x.label().trim());
            f.setType(x.type());
            f.setRequired(x.required());
            f.setPlaceholder(x.placeholder() == null || x.placeholder().isBlank() ? null : x.placeholder().trim());
            f.setPosition(pos++);
            if (x.type().hasOptions()) {
                List<String> opts = x.options() == null ? List.of()
                        : x.options().stream().map(String::trim).distinct().toList();
                if (opts.isEmpty()) {
                    throw ApiException.badRequest(EFormError.FRM_003, f.getLabel(), x.type().name());
                }
                if (opts.size() > FormAnswerValidator.MAX_OPTIONS) {
                    throw ApiException.badRequest(EFormError.FRM_021, FormAnswerValidator.MAX_FIELDS,
                            FormAnswerValidator.MAX_OPTIONS);
                }
                f.setOptions(write(opts));
            }
            out.add(f);
        }
        return out;
    }

    private List<FormField> saveFields(Long formId, List<FormField> built) {
        built.forEach(f -> f.setFormId(formId));
        return fields.saveAll(built);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot write JSON", e);
        }
    }

    /** 256 random bits, URL-safe, unguessable: the link is the only key of a public form. */
    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String newKey() {
        byte[] b = new byte[5];
        RANDOM.nextBytes(b);
        return "f_" + HexFormat.of().formatHex(b);
    }

    private static FormRow row(Form f) {
        return new FormRow(String.valueOf(f.getId()), String.valueOf(f.getCompanyId()), f.getName(), f.isActive(),
                f.getLayout(), f.getPublicToken(), f.getCreatedDate());
    }

    private static FormDto dto(Form f, List<FormField> list) {
        return new FormDto(row(f), list.stream().map(x -> new FieldDto(String.valueOf(x.getId()), x.getFieldKey(),
                x.getLabel(), x.getType(), x.isRequired(), x.getPlaceholder(),
                x.getType().hasOptions() ? readOptions(x.getOptions()) : null)).toList());
    }

    private ResponseDto responseDto(FormResponse r) {
        List<AnswerDto> answers = readAnswers(r.getAnswers()).stream()
                .map(a -> new AnswerDto(a.key(), a.label(), a.type(),
                        a.type() == EType.PASSWORD && a.value() != null ? PASSWORD_MASK : a.value()))
                .toList();
        return new ResponseDto(String.valueOf(r.getId()), String.valueOf(r.getFormId()), answers,
                r.getConvertedTargetType(), r.getConvertedTargetId(), r.getConvertedAt(), r.getCreatedDate());
    }
}
