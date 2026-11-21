package com.terp.erp.employee.document;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.client.MediaClient.MediaFile;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeDtos.DocumentDto;
import com.terp.erp.employee.employee.EmployeeDtos.DocumentForm;
import com.terp.erp.employee.employee.EmployeeDtos.Ref;
import com.terp.erp.employee.org.OrgService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Documents of an employee (docs/analysis/06 §6.1 "Documents" step and tab). The file itself stays in the media
 * library: removing a document removes the link, not the file (it may be used elsewhere; the media library deletes it).
 * Callers have already checked that the employee belongs to the caller's company.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final EmployeeDocumentRepository documents;
    private final OrgService orgs;
    private final MediaClient media;
    private final Clock clock;
    private final com.terp.erp.employee.org.DocumentTypeService documentTypes;

    @Transactional(readOnly = true)
    public List<DocumentDto> list(Long companyId, Long employeeId) {
        Map<Long, String> types = orgs.documentTypeNames(companyId);
        return documents.findByEmployeeIdOrderByIdAsc(employeeId).stream().map(d -> toDto(d, types)).toList();
    }

    /** Ids of the document types Required for the company (effective, system defaults overridden). */
    @Transactional(readOnly = true)
    public java.util.Set<Long> requiredTypeIds(Long companyId) {
        return documentTypes.requiredTypeIds(companyId);
    }

    /**
     * Required types the employee has no document of. TODO(assumption): an expired or unverified document still
     * counts as present (expiry and verification are shown on the document itself) — confirm with BA.
     */
    @Transactional(readOnly = true)
    public List<com.terp.erp.employee.employee.EmployeeDtos.Ref> missingRequired(Long companyId, Long employeeId) {
        java.util.Set<Long> required = documentTypes.requiredTypeIds(companyId);
        if (required.isEmpty()) {
            return List.of();
        }
        java.util.Set<Long> present = documents.findByEmployeeIdOrderByIdAsc(employeeId).stream()
                .map(EmployeeDocument::getDocumentTypeId).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> names = orgs.documentTypeNames(companyId);
        return required.stream().filter(id -> !present.contains(id))
                .map(id -> new com.terp.erp.employee.employee.EmployeeDtos.Ref(id.toString(), names.get(id)))
                .sorted(java.util.Comparator.comparing(com.terp.erp.employee.employee.EmployeeDtos.Ref::name,
                        java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** Type usable by the company, file of the company and at most 5 MB. */
    public com.terp.erp.employee.org.DocumentType assertAcceptable(Long companyId, DocumentForm form) {
        var type = orgs.usableDocumentType(companyId, form.documentTypeId());
        MediaFile f = media.require(form.fileId(), companyId);
        if (f.sizeBytes() > MAX_BYTES) {
            throw ApiException.badRequest(EEmployeeError.EMP_024, "5 MB");
        }
        return type;
    }

    @Transactional
    public DocumentDto add(Long companyId, Long employeeId, DocumentForm form) {
        var type = assertAcceptable(companyId, form);
        EmployeeDocument d = new EmployeeDocument();
        d.setCompanyId(companyId);
        d.setEmployeeId(employeeId);
        d.setDocumentTypeId(type.getId());
        d.setMediaFileId(form.fileId());
        d.setExpiryDate(form.expiryDate());
        return toDto(documents.save(d), Map.of(type.getId(), type.getName()));
    }

    @Transactional
    public DocumentDto verify(Long companyId, Long employeeId, Long documentId, boolean verified) {
        EmployeeDocument d = require(employeeId, documentId);
        d.setVerified(verified);
        d.setVerifiedBy(verified ? CurrentUser.get().keycloakId() : null);
        d.setVerifiedAt(verified ? clock.instant() : null);
        return toDto(d, orgs.documentTypeNames(companyId));
    }

    @Transactional
    public void delete(Long employeeId, Long documentId) {
        documents.delete(require(employeeId, documentId));
    }

    private EmployeeDocument require(Long employeeId, Long documentId) {
        return documents.findByIdAndEmployeeId(documentId, employeeId)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_006));
    }

    private DocumentDto toDto(EmployeeDocument d, Map<Long, String> typeNames) {
        MediaFile f = media.find(d.getMediaFileId());
        LocalDate today = LocalDate.now(clock);
        return new DocumentDto(d.getId().toString(),
                new Ref(d.getDocumentTypeId().toString(), typeNames.get(d.getDocumentTypeId())),
                d.getMediaFileId().toString(),
                f == null ? null : f.displayName(), f == null ? null : f.mimeType(), f == null ? 0 : f.sizeBytes(),
                f == null ? null : f.viewUrl(), f == null ? null : f.downloadUrl(),
                d.getExpiryDate(), d.getExpiryDate() != null && d.getExpiryDate().isBefore(today),
                d.isVerified(), d.getVerifiedAt());
    }
}
