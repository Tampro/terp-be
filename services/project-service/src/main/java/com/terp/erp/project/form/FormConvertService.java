package com.terp.erp.project.form;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.project.client.CrmClient;
import com.terp.erp.project.common.EFormError;
import com.terp.erp.project.form.FormAnswerValidator.Answer;
import com.terp.erp.project.form.FormField.EType;
import com.terp.erp.project.form.FormResponse.ETarget;
import com.terp.erp.project.project.ProjectService;
import com.terp.erp.security.PermissionChecker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one form response into a project (in-process) or a CRM lead, deal or contract (crm-service, as the caller).
 * The target record is built from a mapping {targetField -> form field key}; what a form cannot supply (pipeline,
 * owner, contract type...) comes as fixed values. A mapped non-empty answer wins over a fixed value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormConvertService {
    enum EKind { STRING, LONG, DECIMAL, DATE, LONGS }

    record Target(String name, EKind kind, boolean required) {
    }

    /** Mirrors ProjectForm and crm-service's LeadForm / DealForm / ContractForm (required = @NotNull/@NotBlank). */
    static final Map<ETarget, List<Target>> TARGETS = Map.of(
            ETarget.PROJECT, List.of(t("name", EKind.STRING, true), t("startDate", EKind.DATE, false),
                    t("endDate", EKind.DATE, false), t("budget", EKind.DECIMAL, false),
                    t("description", EKind.STRING, false), t("userIds", EKind.LONGS, false)),
            ETarget.LEAD, List.of(t("pipelineId", EKind.LONG, true), t("stageId", EKind.LONG, false),
                    t("name", EKind.STRING, true), t("email", EKind.STRING, true), t("subject", EKind.STRING, true),
                    t("ownerUserId", EKind.LONG, true), t("phone", EKind.STRING, false),
                    t("followUpDate", EKind.DATE, false), t("notes", EKind.STRING, false)),
            ETarget.DEAL, List.of(t("pipelineId", EKind.LONG, true), t("stageId", EKind.LONG, false),
                    t("name", EKind.STRING, true), t("price", EKind.DECIMAL, true), t("phone", EKind.STRING, false),
                    t("clientIds", EKind.LONGS, false), t("notes", EKind.STRING, false)),
            ETarget.CONTRACT, List.of(t("subject", EKind.STRING, true), t("customerId", EKind.LONG, true),
                    t("contractTypeId", EKind.LONG, true), t("value", EKind.DECIMAL, false),
                    t("startDate", EKind.DATE, true), t("endDate", EKind.DATE, true),
                    t("description", EKind.STRING, false)));

    public record ConvertForm(
            @Schema(description = "PROJECT, CONTRACT, LEAD or DEAL", example = "LEAD",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ETarget target,
            @Schema(description = "Target field -> form field key. LEAD: pipelineId, stageId, name, email, subject,"
                    + " ownerUserId, phone, followUpDate, notes. DEAL: pipelineId, stageId, name, price, phone,"
                    + " clientIds, notes. CONTRACT: subject, customerId, contractTypeId, value, startDate, endDate,"
                    + " description. PROJECT: name, startDate, endDate, budget, description, userIds.",
                    example = "{\"name\": \"full_name\", \"email\": \"email\", \"subject\": \"message\"}")
            Map<String, String> mapping,
            @Schema(description = "Fixed values for target fields a form cannot supply (ids, dates yyyy-MM-dd,"
                    + " comma-separated id lists)", example = "{\"pipelineId\": \"1\", \"ownerUserId\": \"12\"}")
            Map<String, String> values) {
    }

    public record ConvertResult(String responseId, ETarget targetType, String targetId) {
    }

    private final FormService formService;
    private final FormResponseRepository responses;
    private final ProjectService projectService;
    private final CrmClient crm;
    private final PermissionChecker permissions;
    private final Clock clock;

    @Transactional
    public ConvertResult convert(Long formId, Long responseId, ConvertForm form) {
        FormResponse r = formService.requireResponse(formId, responseId, true);
        if (r.getConvertedTargetType() != null) {
            throw ApiException.badRequest(EFormError.FRM_015, r.getConvertedTargetType().name(),
                    r.getConvertedTargetId());
        }
        ETarget target = form.target();
        if (target == ETarget.PROJECT && !permissions.has("PROJECT_MANAGE")) {
            // crm-service checks its own permissions for the other targets; a project is created in-process.
            throw ApiException.forbidden(ECommonError.COMMON_004);
        }
        Map<String, Object> v = resolve(target, formService.readAnswers(r.getAnswers()), form);
        if (target != ETarget.PROJECT) {
            // ISO strings on the wire, whatever the HTTP client's date settings are.
            v.replaceAll((k, x) -> x instanceof LocalDate d ? d.toString() : x);
        }
        String id = switch (target) {
            case LEAD -> crm.createLead(v);
            case DEAL -> crm.createDeal(v);
            case CONTRACT -> {
                // Only a super admin's companyId is read by crm-service; a company user always acts on their own.
                v.put("companyId", r.getCompanyId());
                yield crm.createContract(v);
            }
            case PROJECT -> createProject(r.getCompanyId(), v);
        };
        // TODO(assumption): no outbox: if this commit fails after crm-service created the record, the record stays
        // and the response can be converted again (same gap as user creation vs Keycloak).
        r.setConvertedTargetType(target);
        r.setConvertedTargetId(id);
        r.setConvertedAt(Instant.now(clock));
        responses.save(r);
        log.info("form response {} converted to {} {}", r.getId(), target, id);
        return new ConvertResult(String.valueOf(r.getId()), target, id);
    }

    @SuppressWarnings("unchecked")
    private String createProject(Long companyId, Map<String, Object> v) {
        return projectService.create(new ProjectService.ProjectForm(companyId, (String) v.get("name"),
                (LocalDate) v.get("startDate"), (LocalDate) v.get("endDate"), (BigDecimal) v.get("budget"),
                (String) v.get("description"), (List<Long>) v.getOrDefault("userIds", List.of()))).id();
    }

    /** Typed values by target field name; dates stay LocalDate (Jackson writes them as yyyy-MM-dd). */
    static Map<String, Object> resolve(ETarget target, List<Answer> answers, ConvertForm form) {
        List<Target> spec = TARGETS.get(target);
        Map<String, Target> byName = new HashMap<>();
        spec.forEach(s -> byName.put(s.name(), s));
        Map<String, String> mapping = form.mapping() == null ? Map.of() : form.mapping();
        Map<String, String> fixed = form.values() == null ? Map.of() : form.values();
        for (String k : mapping.keySet()) {
            requireKnown(byName, k, target);
        }
        for (String k : fixed.keySet()) {
            requireKnown(byName, k, target);
        }
        Map<String, Answer> byKey = new HashMap<>();
        answers.forEach(a -> byKey.put(a.key(), a));

        Map<String, Object> out = new LinkedHashMap<>();
        for (Target s : spec) {
            String raw = null;
            String fieldKey = mapping.get(s.name());
            if (fieldKey != null) {
                Answer a = byKey.get(fieldKey);
                if (a == null) {
                    throw ApiException.badRequest(EFormError.FRM_016, fieldKey);
                }
                if (a.type() == EType.PASSWORD) {
                    // Only a hash is stored: never copy it into another record.
                    throw ApiException.badRequest(EFormError.FRM_018, s.name());
                }
                raw = text(a.value());
            }
            if (raw == null || raw.isBlank()) {
                raw = fixed.get(s.name());
            }
            if (raw == null || raw.isBlank()) {
                if (s.required()) {
                    throw ApiException.badRequest(EFormError.FRM_017, target.name(), s.name());
                }
                continue;
            }
            out.put(s.name(), parse(s, raw.trim()));
        }
        return out;
    }

    private static void requireKnown(Map<String, Target> byName, String name, ETarget target) {
        if (!byName.containsKey(name)) {
            throw ApiException.badRequest(EFormError.FRM_023, name, target.name());
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return String.join(", ", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }

    private static Object parse(Target s, String raw) {
        try {
            return switch (s.kind()) {
                case STRING -> raw;
                case LONG -> Long.valueOf(raw);
                case DECIMAL -> new BigDecimal(raw);
                case DATE -> LocalDate.parse(raw);
                case LONGS -> {
                    List<Long> ids = new ArrayList<>();
                    for (String p : Arrays.asList(raw.split(","))) {
                        if (!p.isBlank()) {
                            ids.add(Long.valueOf(p.trim()));
                        }
                    }
                    yield ids;
                }
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw ApiException.badRequest(EFormError.FRM_018, s.name());
        }
    }

    private static Target t(String name, EKind kind, boolean required) {
        return new Target(name, kind, required);
    }
}
