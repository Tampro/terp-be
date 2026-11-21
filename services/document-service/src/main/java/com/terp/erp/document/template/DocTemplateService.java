package com.terp.erp.document.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.document.category.DocumentCategory;
import com.terp.erp.document.category.DocumentCategoryRepository;
import com.terp.erp.document.client.MediaClient;
import com.terp.erp.document.client.MediaClient.MediaFile;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.common.Texts;
import com.terp.erp.document.contract.ContractType;
import com.terp.erp.document.contract.ContractTypeRepository;
import com.terp.erp.document.contract.EmployeeContract;
import com.terp.erp.document.contract.EmployeeContractService;
import com.terp.erp.document.template.DocTemplate.EFormat;
import com.terp.erp.document.template.DocTemplate.EKind;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contract Templates (§9.4.4) and Document Templates (§9.4.5) of docs/analysis/06 — one service, the kind decides the
 * group (contract type or category) and the permission (controller). Generate fills {{variables}} (given values over
 * the default values; a missing one is DOC_021) and renders a PDF; a contract template can attach it to a contract.
 */
@Service
@RequiredArgsConstructor
public class DocTemplateService {
    private final DocTemplateRepository templates;
    private final ContractTypeRepository contractTypes;
    private final DocumentCategoryRepository categories;
    private final EmployeeContractService contracts;
    private final MediaClient media;
    private final ObjectMapper mapper;

    public record TemplateForm(
            @Schema(example = "Internship Agreement Template", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Schema(description = "Contract type (contract templates) or category (document templates)")
            Long groupId,
            @Schema(description = "PDF only; omitted = PDF") EFormat fileFormat,
            @Schema(description = "ACTIVE or INACTIVE (the form's Draft); omitted = ACTIVE on create, kept on edit")
            EStatus status,
            @Schema(description = "Set as Default for its contract type / category") Boolean defaultTemplate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "This agreement is made with {{intern_name}}.")
            @NotBlank @Size(max = 200000) String content,
            @Schema(description = "Comma-separated variable names", example = "intern_name, start_date")
            @Size(max = 4000) String variables,
            @Schema(description = "Document templates: JSON object of default values", example =
                    "{\"company_name\":\"Test Co\"}") @Size(max = 20000) String defaultValues) {
    }

    public record GroupRef(String id, String name) {
    }

    /** content only on the details; variableCount / clauseCount / contentLength = the list columns. */
    public record TemplateDto(String id, EKind kind, String name, String description, GroupRef group,
                              EFormat fileFormat, EStatus status, boolean defaultTemplate, List<String> variables,
                              int variableCount, int clauseCount, int contentLength, Map<String, String> defaultValues,
                              String content, Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record Preview(String name, String text) {
    }

    public record Generated(String fileName, byte[] pdf, String attachedFileId) {
    }

    @Transactional(readOnly = true)
    public Page<TemplateDto> search(EKind kind, Long requestedCompanyId, String keyword, Long groupId, EStatus status,
                                    Pageable pageable) {
        Page<DocTemplate> page = templates.search(Texts.company(requestedCompanyId), kind, groupId, status,
                Texts.kw(keyword), pageable);
        Map<Long, String> names = groupNames(kind, page.getContent());
        return page.map(t -> dto(t, names, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(EKind kind, Long requestedCompanyId, String keyword, Long groupId) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : templates.countByStatus(Texts.company(requestedCompanyId), kind, groupId, Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public TemplateDto get(EKind kind, Long id) {
        DocTemplate t = require(kind, id);
        return dto(t, groupNames(kind, List.of(t)), true);
    }

    @Transactional
    public TemplateDto create(EKind kind, Long requestedCompanyId, TemplateForm form) {
        DocTemplate t = new DocTemplate();
        t.setCompanyId(Texts.company(requestedCompanyId));
        t.setKind(kind);
        apply(t, form);
        templates.saveAndFlush(t);
        makeDefault(t);
        return get(kind, t.getId());
    }

    /** Full replacement; status and default omitted = kept. */
    @Transactional
    public TemplateDto update(EKind kind, Long id, TemplateForm form) {
        DocTemplate t = require(kind, id);
        apply(t, form);
        makeDefault(t);
        return get(kind, id);
    }

    @Transactional
    public TemplateDto changeStatus(EKind kind, Long id, EStatus status) {
        require(kind, id).setStatus(status);
        return get(kind, id);
    }

    @Transactional
    public void delete(EKind kind, Long id) {
        require(kind, id).setDeleted(true);
    }

    /** Preview: the content with the default values, [name] for the others. */
    @Transactional(readOnly = true)
    public Preview preview(EKind kind, Long id) {
        DocTemplate t = require(kind, id);
        return new Preview(t.getName(), TemplateText.fill(t.getContent(), defaults(t), true));
    }

    /**
     * Generate: given values over the defaults; contractId (contract templates) uploads the PDF to the media library
     * and attaches it to that contract.
     */
    @Transactional
    public Generated generate(EKind kind, Long id, Map<String, String> values, Long contractId) {
        DocTemplate t = require(kind, id);
        if (t.getStatus() != EStatus.ACTIVE) {
            throw ApiException.badRequest(EDocumentError.DOC_020);
        }
        Map<String, String> all = new HashMap<>(defaults(t));
        if (values != null) {
            values.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    all.put(k, v);
                }
            });
        }
        byte[] pdf = TemplatePdf.render(t.getName(), TemplateText.fill(t.getContent(), all, false));
        String fileName = slug(t.getName()) + ".pdf";
        String attached = null;
        if (kind == EKind.CONTRACT && contractId != null) {
            EmployeeContract c = contracts.require(contractId);
            String name = (c.getContractNumber() == null ? "contract-" + c.getId() : c.getContractNumber()) + ".pdf";
            MediaFile f = media.upload(c.getCompanyId(), name, pdf);
            c.setDocumentFileId(Long.valueOf(f.id()));
            attached = f.id();
            fileName = name;
        }
        return new Generated(fileName, pdf, attached);
    }

    private void apply(DocTemplate t, TemplateForm f) {
        Long group = f.groupId();
        if (group != null) {
            boolean ok = t.getKind() == EKind.CONTRACT
                    ? contractTypes.findByIdAndDeletedFalse(group).filter(x -> x.getCompanyId().equals(t.getCompanyId()))
                    .isPresent()
                    : categories.findByIdAndDeletedFalse(group).filter(x -> x.getCompanyId().equals(t.getCompanyId()))
                    .isPresent();
            if (!ok) {
                throw ApiException.badRequest(t.getKind() == EKind.CONTRACT ? EDocumentError.DOC_005
                        : EDocumentError.DOC_002);
            }
        }
        List<String> vars = TemplateText.variables(f.variables(), f.content());
        String defaults = null;
        if (t.getKind() == EKind.DOCUMENT && f.defaultValues() != null && !f.defaultValues().isBlank()) {
            defaults = normalizedDefaults(f.defaultValues());
        }
        t.setName(f.name().trim());
        t.setDescription(Texts.blankToNull(f.description()));
        t.setContractTypeId(t.getKind() == EKind.CONTRACT ? group : null);
        t.setCategoryId(t.getKind() == EKind.DOCUMENT ? group : null);
        t.setFileFormat(f.fileFormat() == null ? EFormat.PDF : f.fileFormat());
        if (f.status() != null) {
            t.setStatus(f.status());
        }
        if (f.defaultTemplate() != null) {
            t.setDefaultTemplate(f.defaultTemplate());
        }
        t.setContent(f.content());
        t.setVariables(String.join(",", vars));
        t.setDefaultValues(defaults);
    }

    /** One default per kind and group. */
    private void makeDefault(DocTemplate t) {
        Long group = t.getKind() == EKind.CONTRACT ? t.getContractTypeId() : t.getCategoryId();
        if (t.isDefaultTemplate() && group != null) {
            templates.clearDefault(t.getCompanyId(), t.getKind(), group, t.getId());
        }
    }

    /** A flat JSON object of text (numbers / booleans are kept as text), DOC_023 otherwise. */
    private String normalizedDefaults(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            if (n == null || !n.isObject()) {
                throw ApiException.badRequest(EDocumentError.DOC_023);
            }
            Map<String, String> out = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> {
                if (!e.getValue().isValueNode() || !TemplateText.NAME.matcher(e.getKey()).matches()) {
                    throw ApiException.badRequest(EDocumentError.DOC_023);
                }
                out.put(e.getKey(), e.getValue().asText());
            });
            return mapper.writeValueAsString(out);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw ApiException.badRequest(EDocumentError.DOC_023);
        }
    }

    private Map<String, String> defaults(DocTemplate t) {
        if (t.getDefaultValues() == null) {
            return Map.of();
        }
        try {
            return mapper.readValue(t.getDefaultValues(), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Map.of();
        }
    }

    private Map<Long, String> groupNames(EKind kind, List<DocTemplate> list) {
        List<Long> ids = list.stream().map(t -> kind == EKind.CONTRACT ? t.getContractTypeId() : t.getCategoryId())
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return kind == EKind.CONTRACT
                ? contractTypes.findAllById(ids).stream().collect(Collectors.toMap(ContractType::getId,
                ContractType::getName))
                : categories.findAllById(ids).stream().collect(Collectors.toMap(DocumentCategory::getId,
                DocumentCategory::getName));
    }

    private TemplateDto dto(DocTemplate t, Map<Long, String> names, boolean withContent) {
        Long group = t.getKind() == EKind.CONTRACT ? t.getContractTypeId() : t.getCategoryId();
        List<String> vars = t.getVariables() == null || t.getVariables().isBlank() ? List.of()
                : List.of(t.getVariables().split(","));
        return new TemplateDto(t.getId().toString(), t.getKind(), t.getName(), t.getDescription(),
                group == null ? null : new GroupRef(group.toString(), names.get(group)), t.getFileFormat(),
                t.getStatus(), t.isDefaultTemplate(), vars, vars.size(), TemplateText.clauses(t.getContent()),
                t.getContent().length(), defaults(t), withContent ? t.getContent() : null, t.getCreatedDate());
    }

    static String slug(String name) {
        String s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "document" : s;
    }

    private DocTemplate require(EKind kind, Long id) {
        DocTemplate t = templates.findByIdAndKindAndDeletedFalse(id, kind)
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_019));
        TenantGuard.assertSameCompany(t.getCompanyId(), EDocumentError.DOC_019);
        return t;
    }
}
