package com.terp.erp.support.ticket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.Texts;
import com.terp.erp.support.ticket.TicketField.EFieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Support ticket custom fields (ERP manual 19 "Support ticket fields"; reference SupportTicket
 * Company/SettingsController + ticket_fields / ticket_field_values). Values are validated against the company's
 * active fields: same company, active, given once, required ones present, value of the field's type, a SELECT
 * value one of its options.
 */
@Service
@RequiredArgsConstructor
public class TicketFieldService {
    private static final Set<Integer> WIDTHS = Set.of(3, 4, 6, 12);
    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    public record FieldDto(String id, String label, String placeholder, EFieldType type, List<String> options,
                           boolean required, int width, int order, boolean active) {
    }

    public record FieldForm(
            @Schema(example = "Order number", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String label,
            @Schema(example = "e.g. SO-0001") @Size(max = 255) String placeholder,
            @Schema(example = "TEXT", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EFieldType type,
            @Schema(description = "SELECT only (required there, 1-100 distinct values); ignored otherwise",
                    example = "[\"Low\",\"High\"]") @Size(max = 100) List<@NotBlank @Size(max = 255) String> options,
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean required,
            @Schema(description = "Grid columns of 12: 3, 4, 6 or 12", example = "6",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer width,
            @Schema(description = "Display order, ascending; null = 0", example = "1") @Min(0) @Max(10_000) Integer order,
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean active) {
    }

    /** A value sent with a ticket. */
    public record CustomFieldInput(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long fieldId,
            @Schema(description = "TEXT ≤ 255, TEXTAREA ≤ 2000, EMAIL, NUMBER (decimal), DATE (yyyy-MM-dd), SELECT "
                    + "(one of options); blank = not given", example = "SO-0001") @Size(max = 2000) String value) {
    }

    /** A stored value, with the field's current label. */
    public record CustomFieldValue(String fieldId, String label, EFieldType type, String value) {
    }

    private final TicketFieldRepository fields;
    private final TicketFieldValueRepository values;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<FieldDto> list(Long requestedCompanyId) {
        return fields.findByCompanyIdAndDeletedFalseOrderBySortOrderAscIdAsc(Texts.company(requestedCompanyId))
                .stream().map(this::dto).toList();
    }

    /** For the portal form: active fields in order. */
    @Transactional(readOnly = true)
    public List<FieldDto> activeOf(Long companyId) {
        return fields.findByCompanyIdAndActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc(companyId).stream()
                .map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public FieldDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public FieldDto create(Long requestedCompanyId, FieldForm f) {
        TicketField t = new TicketField();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, f);
        return dto(fields.save(t));
    }

    /** Full replacement. */
    @Transactional
    public FieldDto update(Long id, FieldForm f) {
        TicketField t = require(id);
        apply(t, f);
        return dto(t);
    }

    /** Soft delete: the tickets keep their values and still show the label. */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    // ---- ticket values

    /**
     * Checks the values against the company's active fields and returns fieldId → normalised value (blank ones
     * dropped). Required active fields must be present.
     */
    public Map<Long, String> validate(Long companyId, List<CustomFieldInput> input) {
        List<TicketField> active = fields.findByCompanyIdAndActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc(companyId);
        Map<Long, TicketField> byId = active.stream().collect(Collectors.toMap(TicketField::getId, Function.identity()));
        Map<Long, String> out = new LinkedHashMap<>();
        for (CustomFieldInput in : input == null ? List.<CustomFieldInput>of() : input) {
            if (in == null || in.fieldId() == null) {
                continue;
            }
            TicketField f = byId.get(in.fieldId());
            if (f == null) {
                throw ApiException.badRequest(ESupportError.SUP_035, in.fieldId());
            }
            if (out.containsKey(f.getId())) {
                throw ApiException.badRequest(ESupportError.SUP_036, f.getLabel());
            }
            String v = Texts.blankToNull(in.value());
            if (v != null) {
                out.put(f.getId(), check(f, v.trim()));
            }
        }
        for (TicketField f : active) {
            if (f.isRequired() && !out.containsKey(f.getId())) {
                throw ApiException.badRequest(ESupportError.SUP_037, f.getLabel());
            }
        }
        return out;
    }

    /** Replaces the ticket's values of the company's active fields; values of inactive / deleted fields stay. */
    public void store(Long companyId, Long ticketId, Map<Long, String> checked) {
        Set<Long> active = fields.findByCompanyIdAndActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc(companyId)
                .stream().map(TicketField::getId).collect(Collectors.toSet());
        List<TicketFieldValue> old = values.findByTicketIdOrderByIdAsc(ticketId).stream()
                .filter(v -> active.contains(v.getFieldId())).toList();
        values.deleteAll(old);
        values.flush();
        List<TicketFieldValue> rows = new ArrayList<>();
        checked.forEach((fieldId, value) -> {
            TicketFieldValue v = new TicketFieldValue();
            v.setTicketId(ticketId);
            v.setFieldId(fieldId);
            v.setValue(value);
            rows.add(v);
        });
        values.saveAll(rows);
    }

    /** The ticket's values in field order, with current labels (deleted fields included). */
    public List<CustomFieldValue> valuesOf(Long ticketId) {
        List<TicketFieldValue> list = values.findByTicketIdOrderByIdAsc(ticketId);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, TicketField> byId = new HashMap<>();
        fields.findAllById(list.stream().map(TicketFieldValue::getFieldId).distinct().toList())
                .forEach(f -> byId.put(f.getId(), f));
        return list.stream().filter(v -> byId.containsKey(v.getFieldId()))
                .sorted(Comparator.comparing((TicketFieldValue v) -> byId.get(v.getFieldId()).getSortOrder())
                        .thenComparing(TicketFieldValue::getFieldId))
                .map(v -> {
                    TicketField f = byId.get(v.getFieldId());
                    return new CustomFieldValue(f.getId().toString(), f.getLabel(), f.getType(), v.getValue());
                }).toList();
    }

    // ----

    private String check(TicketField f, String v) {
        switch (f.getType()) {
            case TEXT -> {
                if (v.length() > 255) {
                    throw invalid(f);
                }
            }
            case TEXTAREA -> {
                // ≤ 2000: the form's @Size.
            }
            case EMAIL -> {
                if (v.length() > 255 || !v.matches(EMAIL)) {
                    throw invalid(f);
                }
                return v.toLowerCase(java.util.Locale.ROOT);
            }
            case NUMBER -> {
                try {
                    return new BigDecimal(v).toPlainString();
                } catch (NumberFormatException e) {
                    throw invalid(f);
                }
            }
            case DATE -> {
                try {
                    return LocalDate.parse(v).toString();
                } catch (DateTimeParseException e) {
                    throw invalid(f);
                }
            }
            case SELECT -> {
                if (!options(f).contains(v)) {
                    throw ApiException.badRequest(ESupportError.SUP_039, f.getLabel(), v);
                }
            }
        }
        return v;
    }

    private static ApiException invalid(TicketField f) {
        return ApiException.badRequest(ESupportError.SUP_038, f.getLabel(), f.getType().name());
    }

    private void apply(TicketField t, FieldForm f) {
        if (!WIDTHS.contains(f.width())) {
            throw ApiException.badRequest(ESupportError.SUP_041);
        }
        List<String> opts = null;
        if (f.type() == EFieldType.SELECT) {
            opts = f.options() == null ? List.of() : f.options().stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).distinct().toList();
            if (opts.isEmpty()) {
                throw ApiException.badRequest(ESupportError.SUP_040);
            }
        }
        t.setLabel(f.label().trim());
        t.setPlaceholder(Texts.blankToNull(f.placeholder()));
        t.setType(f.type());
        t.setOptionsJson(opts == null ? null : write(opts));
        t.setRequired(f.required());
        t.setWidth(f.width());
        t.setSortOrder(f.order() == null ? 0 : f.order());
        t.setActive(f.active());
    }

    private TicketField require(Long id) {
        TicketField t = fields.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESupportError.SUP_042));
        TenantGuard.assertSameCompany(t.getCompanyId(), ESupportError.SUP_042);
        return t;
    }

    private List<String> options(TicketField f) {
        if (f.getOptionsJson() == null) {
            return List.of();
        }
        try {
            return mapper.readValue(f.getOptionsJson(), new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String write(List<String> opts) {
        try {
            return mapper.writeValueAsString(opts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("options not serializable", e);
        }
    }

    private FieldDto dto(TicketField t) {
        return new FieldDto(t.getId().toString(), t.getLabel(), t.getPlaceholder(), t.getType(),
                t.getType() == EFieldType.SELECT ? options(t) : List.of(), t.isRequired(), t.getWidth(),
                t.getSortOrder(), t.isActive());
    }
}
