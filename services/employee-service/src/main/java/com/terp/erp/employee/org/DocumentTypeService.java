package com.terp.erp.employee.org;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.document.EmployeeDocumentRepository;
import com.terp.erp.employee.org.DocumentTypeDtos.DocumentTypeDto;
import com.terp.erp.employee.org.DocumentTypeDtos.DocumentTypeForm;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Document Types page (docs/analysis/06 §6.3.7). System types are shared and read-only, but each company sets them
 * Required or Optional; company types are fully editable. Required is tracked (missing documents of an employee),
 * never enforced when adding one (PO, 2026-11-19).
 */
@Service
@RequiredArgsConstructor
public class DocumentTypeService {
    private final DocumentTypeRepository types;
    private final DocumentTypeSettingRepository settings;
    private final EmployeeDocumentRepository documents;

    /**
     * System types first, then the company's own, by name. A company has a handful of types, so filtering and paging
     * happen in memory (the effective Required of system types lives in another table).
     */
    @Transactional(readOnly = true)
    public Page<DocumentTypeDto> search(Long requestedCompanyId, String keyword, Boolean required, Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<DocumentTypeDto> all = dtos(companyId).stream()
                .filter(d -> kw.isEmpty() || d.name().toLowerCase(Locale.ROOT).contains(kw)
                        || (d.description() != null && d.description().toLowerCase(Locale.ROOT).contains(kw)))
                .filter(d -> required == null || d.required() == required)
                .toList();
        if (pageable.isUnpaged()) {
            return new PageImpl<>(all);
        }
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /** Every type the company can use, for the Add Employee documents step. */
    @Transactional(readOnly = true)
    public List<DocumentTypeDto> options(Long requestedCompanyId) {
        return dtos(company(requestedCompanyId));
    }

    @Transactional(readOnly = true)
    public DocumentTypeDto get(Long id) {
        DocumentType t = requireVisible(id);
        Long companyId = companyOf(t);
        return DocumentTypeDto.of(t, effectiveRequired(t, companyId == null ? Map.of() : requiredSettings(companyId)));
    }

    @Transactional
    public DocumentTypeDto create(Long requestedCompanyId, DocumentTypeForm form) {
        Long companyId = company(requestedCompanyId);
        DocumentType t = new DocumentType();
        t.setCompanyId(companyId);
        apply(t, form, -1L);
        return DocumentTypeDto.of(types.save(t), t.isRequired());
    }

    /** Full replacement of a company type: omitted description is cleared, omitted required = Optional. */
    @Transactional
    public DocumentTypeDto update(Long id, DocumentTypeForm form) {
        DocumentType t = requireOwn(id);
        apply(t, form, id);
        return DocumentTypeDto.of(t, t.isRequired());
    }

    /**
     * Required/Optional of any usable type: a company type is changed in place, a system type gets the company's own
     * setting. A super admin must name the company for a system type.
     */
    @Transactional
    public DocumentTypeDto setRequired(Long requestedCompanyId, Long id, boolean required) {
        DocumentType t = requireVisible(id);
        if (t.getCompanyId() != null) {
            t.setRequired(required);
            return DocumentTypeDto.of(t, required);
        }
        Long companyId = company(requestedCompanyId);
        DocumentTypeSetting s = settings.findByCompanyIdAndDocumentTypeId(companyId, id).orElseGet(() -> {
            DocumentTypeSetting n = new DocumentTypeSetting();
            n.setCompanyId(companyId);
            n.setDocumentTypeId(id);
            return n;
        });
        s.setRequired(required);
        settings.save(s);
        return DocumentTypeDto.of(t, required);
    }

    @Transactional
    public void delete(Long id) {
        DocumentType t = requireOwn(id);
        if (documents.existsByDocumentTypeId(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        t.setDeleted(true);
    }

    /** Ids of the types Required for the company (effective: company setting over the system default). */
    @Transactional(readOnly = true)
    public Set<Long> requiredTypeIds(Long companyId) {
        Map<Long, Boolean> s = requiredSettings(companyId);
        return types.visibleTo(companyId).stream().filter(t -> effectiveRequired(t, s)).map(DocumentType::getId)
                .collect(Collectors.toSet());
    }

    private List<DocumentTypeDto> dtos(Long companyId) {
        Map<Long, Boolean> s = requiredSettings(companyId);
        return types.visibleTo(companyId).stream().map(t -> DocumentTypeDto.of(t, effectiveRequired(t, s))).toList();
    }

    private Map<Long, Boolean> requiredSettings(Long companyId) {
        return settings.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(DocumentTypeSetting::getDocumentTypeId, DocumentTypeSetting::isRequired));
    }

    private static boolean effectiveRequired(DocumentType t, Map<Long, Boolean> companySettings) {
        return t.getCompanyId() == null ? companySettings.getOrDefault(t.getId(), t.isRequired()) : t.isRequired();
    }

    private void apply(DocumentType t, DocumentTypeForm f, Long selfId) {
        String name = f.name().trim();
        if (types.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        t.setName(name);
        t.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        t.setRequired(f.required());
    }

    /** System type or one of the caller's company (404 otherwise). */
    private DocumentType requireVisible(Long id) {
        DocumentType t = types.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_005));
        if (t.getCompanyId() != null) {
            TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_005);
        }
        return t;
    }

    /** System types (company null) are read-only for everyone, the super admin included: they are seeded. */
    private DocumentType requireOwn(Long id) {
        DocumentType t = requireVisible(id);
        if (t.getCompanyId() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_021);
        }
        return t;
    }

    /** The company whose Required applies on a detail: the type's own, else the caller's (none for a super admin). */
    private static Long companyOf(DocumentType t) {
        if (t.getCompanyId() != null) {
            return t.getCompanyId();
        }
        return com.terp.erp.security.CurrentUser.find().map(p -> p.companyId()).filter(Objects::nonNull).orElse(null);
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
